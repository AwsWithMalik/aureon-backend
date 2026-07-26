package com.Accounting.app.AI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.Accounting.app.AI.dto.AgentPdfReportResponse;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.files.DocumentType;
import com.Accounting.app.files.FileRepo;
import com.Accounting.app.files.ReceiptExtraction;
import com.Accounting.app.files.ReceiptExtractionItem;
import com.Accounting.app.files.ReceiptExtractionRepo;
import com.Accounting.app.files.UploadedFile;
import com.Accounting.app.settings.SettingsPageServices;
import com.Accounting.app.tax.TaxProfilePageServices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AIservice {
    private static final Logger log = LoggerFactory.getLogger(AIservice.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String aiServiceBaseUrl;
    private final AgentMemoryService agentMemoryService;
    private final UserRepo userRepo;
    private final SettingsPageServices settingsPageServices;
    private final TaxProfilePageServices taxProfilePageServices;
    private final ReceiptExtractionRepo receiptExtractionRepo;
    private final FileRepo fileRepo;

    private record SessionContextPayload(
            List<Map<String, Object>> sessionMemory,
            Map<String, Object> fileContext) {
    }

    public AIservice(
            AgentMemoryService agentMemoryService,
            UserRepo userRepo,
            SettingsPageServices settingsPageServices,
            TaxProfilePageServices taxProfilePageServices,
            ReceiptExtractionRepo receiptExtractionRepo,
            FileRepo fileRepo,
            @Value("${app.ai-service.base-url:http://ai-layer:8000}") String aiServiceBaseUrl) {
        this.agentMemoryService = agentMemoryService;
        this.userRepo = userRepo;
        this.settingsPageServices = settingsPageServices;
        this.taxProfilePageServices = taxProfilePageServices;
        this.receiptExtractionRepo = receiptExtractionRepo;
        this.fileRepo = fileRepo;
        this.aiServiceBaseUrl = normalizeBaseUrl(aiServiceBaseUrl);
    }

    public String processReceipt(MultipartFile file, Integer userId, Integer documentId) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", multipartFileEntity(file, "receipt"));
        body.add("user_id", userId);
        body.add("document_id", documentId);

        return restTemplate.postForObject(
                aiUrl("/receipts/process"),
                new HttpEntity<>(body, multipartHeaders()),
                String.class);
    }

    public AgentFileAnalysisResult analyzeAgentFile(String email, MultipartFile file, String prompt, String sessionId) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", multipartFileEntity(file, "agent-upload"));
            body.add("user_id", user.getUserId());
            body.add("prompt", defaultPrompt(prompt));
            if (sessionId != null && !sessionId.isBlank()) {
                body.add("session_id", sessionId);
            }

            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiUrl("/agent/file-analyze"),
                    new HttpEntity<>(body, multipartHeaders()),
                    String.class);
            return parseAgentFileAnalysisResult(response.getBody());
        } catch (IOException | HttpMessageConversionException | RestClientException ex) {
            log.warn("Agent file analysis failed for user {} and file {}: {}",
                    email,
                    file.getOriginalFilename(),
                    ex.getMessage(),
                    ex);
            return new AgentFileAnalysisResult(
                    agentErrorReply("The agent file analysis service could not process this upload. " + ex.getMessage()),
                    null);
        } catch (Exception ex) {
            log.error("Unexpected agent file analysis failure for user {} and file {}", email,
                    file.getOriginalFilename(), ex);
            return new AgentFileAnalysisResult(
                    agentErrorReply("The agent file analysis pipeline failed. " + ex.getMessage()),
                    null);
        }
    }

    public String syncUserProfileContext(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return restTemplate.postForObject(
                aiUrl("/context/user-profile"),
                Map.of(
                        "userId", user.getUserId(),
                        "email", user.getEmail(),
                        "profile", profilePayload(user.getUserId())),
                String.class);
    }

    public String syncTaxProfileContext(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return restTemplate.postForObject(
                aiUrl("/context/tax-profile"),
                Map.of(
                        "userId", user.getUserId(),
                        "email", user.getEmail(),
                        "taxProfile", taxProfilePayload(user.getEmail())),
                String.class);
    }

    public String askAgentQuestion(String email, String question, List<AgentSessionMessage> sessionMessages) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        SessionContextPayload sessionContext = sessionContextPayload(sessionMessages);
        requestBody.put("userId", user.getUserId());
        requestBody.put("question", question);
        requestBody.put("userProfile", profilePayload(user.getUserId()));
        requestBody.put("taxProfile", taxProfilePayload(user.getEmail()));
        requestBody.put("memories", safeMemoryPayload(user));
        requestBody.put("sessionMemory", sessionContext.sessionMemory());
        if (!sessionContext.fileContext().isEmpty()) {
            requestBody.put("fileContext", sessionContext.fileContext());
        }
        List<Map<String, Object>> taxDocumentContext = taxDocumentContextPayload(user.getEmail());
        if (!taxDocumentContext.isEmpty()) {
            requestBody.put("taxDocumentContext", Map.of("uploadedTaxDocuments", taxDocumentContext));
        }

        try {
            log.info("Calling tax advisor for user {} at {}", user.getEmail(), aiUrl("/agent/tax-advisor"));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiUrl("/agent/tax-advisor"),
                    new HttpEntity<>(requestBody, headers),
                    String.class);
            String responseBody = response.getBody();
            log.info("Tax advisor returned {} for user {}", response.getStatusCode(), user.getEmail());
            applyMemoryActions(user, responseBody);
            String formattedReply = formatAgentReply(responseBody);
            return formattedReply == null || formattedReply.isBlank()
                    ? agentErrorReply("The tax advisor service returned an empty response.")
                    : formattedReply;
        } catch (HttpMessageConversionException | RestClientException ex) {
            log.warn("Tax advisor call failed for user {}: {}", user.getEmail(), ex.getMessage(), ex);
            return agentErrorReply("The tax advisor service could not be reached. " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected agent pipeline failure for user {}", user.getEmail(), ex);
            return agentErrorReply("The agent pipeline failed before it could produce an answer. " + ex.getMessage());
        }
    }

    public AgentPdfReportResponse createAgentPdfReport(String email, JsonNode agentResponse) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("userId", user.getUserId());
            requestBody.put("agentResponse", jsonSerializablePayload(agentResponse));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiUrl("/agent/reports/pdf"),
                    new HttpEntity<>(requestBody, headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String fileName = root.path("fileName").asText(null);
            if (!isSafeReportFileName(fileName)) {
                throw new InvalidInputException("PDF report service returned an invalid file name");
            }

            return new AgentPdfReportResponse(
                    fileName,
                    "/api/agent/reports/" + fileName);
        } catch (InvalidInputException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Agent PDF report generation failed for user {}: {}", email, ex.getMessage(), ex);
            throw new InvalidInputException("Agent PDF report could not be generated");
        }
    }

    public byte[] downloadAgentPdfReport(String email, String fileName) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!isSafeReportFileName(fileName)) {
            throw new InvalidInputException("Invalid report file name");
        }
        if (!fileName.startsWith("agent_report_user_" + user.getUserId() + "_")) {
            throw new InvalidInputException("Report does not belong to the current user");
        }

        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    aiUrl("/reports/" + fileName),
                    byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new InvalidInputException("PDF report was empty");
            }
            return body;
        } catch (InvalidInputException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Agent PDF report download failed for {}: {}", fileName, ex.getMessage(), ex);
            throw new InvalidInputException("Agent PDF report could not be downloaded");
        }
    }

    private boolean isSafeReportFileName(String fileName) {
        return fileName != null
                && fileName.matches("agent_report_user_[0-9]+_[0-9]{8}_[0-9]{6}\\.pdf");
    }

    private Object jsonSerializablePayload(JsonNode node) throws IOException {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return objectMapper.readValue(objectMapper.writeValueAsString(node), Object.class);
    }

    private List<Map<String, Object>> safeMemoryPayload(User user) {
        try {
            return memoryPayload(user.getUserId());
        } catch (Exception ex) {
            log.warn("Agent memory lookup failed for user {}; continuing without memory: {}", user.getEmail(),
                    ex.getMessage(), ex);
            return List.of();
        }
    }

    private HttpEntity<ByteArrayResource> multipartFileEntity(MultipartFile file, String fallbackFileName)
            throws IOException {
        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                String originalFilename = file.getOriginalFilename();
                return originalFilename != null && !originalFilename.isBlank()
                        ? originalFilename
                        : fallbackFileName;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        }
        return new HttpEntity<>(fileResource, fileHeaders);
    }

    private HttpHeaders multipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String defaultPrompt(String prompt) {
        return prompt == null || prompt.isBlank()
                ? "Analyze this uploaded file and explain the important accounting or tax details."
                : prompt.trim();
    }

    private String agentErrorReply(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Agent service issue");
        payload.put("summary", message);
        payload.put("confidence", "low");
        payload.put("blocks", List.of(Map.of(
                "type", "callout_boxes",
                "callouts", List.of(Map.of(
                        "title", "Backend connection problem",
                        "detail", message,
                        "tone", "rose")))));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return message;
        }
    }

    private String formatAgentReply(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isObject() || root.isArray()) {
                return objectMapper.writeValueAsString(root);
            }
        } catch (Exception ex) {
            log.debug("Agent response was not JSON, returning raw text");
        }

        return responseBody.trim();
    }

    private AgentFileAnalysisResult parseAgentFileAnalysisResult(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new AgentFileAnalysisResult(null, null);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode answer = root.path("answer");
            JsonNode fileContext = root.path("fileContext");

            if (!answer.isMissingNode()) {
                String answerJson = answer.isNull() ? null : objectMapper.writeValueAsString(answer);
                String extractedDataJson = fileContext.isMissingNode() || fileContext.isNull()
                        ? null
                        : objectMapper.writeValueAsString(fileContext);
                return new AgentFileAnalysisResult(answerJson, extractedDataJson);
            }

            if (root.isObject() || root.isArray()) {
                return new AgentFileAnalysisResult(objectMapper.writeValueAsString(root), null);
            }
        } catch (Exception ex) {
            log.debug("Agent file response was not JSON, returning raw text");
        }

        return new AgentFileAnalysisResult(responseBody.trim(), null);
    }

    private void applyMemoryActions(User user, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isObject()) {
                return;
            }

            JsonNode memoryActions = root.path("memoryActions");
            if (!memoryActions.isArray() || memoryActions.isEmpty()) {
                log.info("No agent memory actions returned for user {}", user.getEmail());
                return;
            }

            log.info("Processing {} agent memory action(s) for user {}", memoryActions.size(), user.getEmail());
            for (JsonNode actionNode : memoryActions) {
                try {
                    applyMemoryAction(user, actionNode);
                } catch (Exception ex) {
                    log.warn("Agent memory action failed for user {} with payload {}: {}",
                            user.getEmail(),
                            actionNode,
                            ex.getMessage(),
                            ex);
                }
            }
        } catch (Exception ex) {
            log.warn("Agent memory writeback parsing failed for user {}: {}", user.getEmail(), ex.getMessage(), ex);
        }
    }

    private void applyMemoryAction(User user, JsonNode actionNode) {
        if (actionNode == null || !actionNode.isObject()) {
            return;
        }

        String action = safeUpper(actionNode.path("action").asText(""));
        String memoryType = safeUpper(actionNode.path("type").asText(""));
        String content = actionNode.path("content").asText("");
        Double confidence = actionNode.path("confidence").isNumber()
                ? actionNode.path("confidence").asDouble()
                : null;
        Integer memoryId = parseInteger(actionNode.path("memoryId").asText(null));

        switch (action) {
            case "CREATE" -> {
                AgentMemory created = agentMemoryService.createMemory(
                        user.getUserId(),
                        new com.Accounting.app.AI.dto.AgentMemoryRequest(null, memoryType, content, confidence));
                log.info("Created agent memory {} for user {} with type {}", created.getId(), user.getEmail(),
                        memoryType);
            }
            case "UPDATE" -> {
                if (memoryId != null) {
                    AgentMemory updated = agentMemoryService.updateMemory(
                            user.getUserId(),
                            memoryId,
                            new com.Accounting.app.AI.dto.AgentMemoryRequest(null, memoryType, content, confidence));
                    log.info("Updated agent memory {} for user {} with type {}", updated.getId(), user.getEmail(),
                            memoryType);
                } else {
                    log.warn("Skipping UPDATE memory action for user {} because memoryId was missing", user.getEmail());
                }
            }
            case "DELETE" -> {
                if (memoryId != null) {
                    agentMemoryService.deleteMemory(user.getUserId(), memoryId);
                    log.info("Deleted agent memory {} for user {}", memoryId, user.getEmail());
                } else {
                    log.warn("Skipping DELETE memory action for user {} because memoryId was missing", user.getEmail());
                }
            }
            default -> {
                log.debug("Ignoring unsupported memory action '{}' for user {}", action, user.getEmail());
            }
        }
        log.info("Applied agent memory action {} for user {} with type {}", action, user.getEmail(), memoryType);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private Map<String, Object> profilePayload(Integer userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        User user = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        payload.put("user", Map.of(
                "id", user.getUserId(),
                "email", user.getEmail(),
                "name", user.getName()));
        payload.put("settings", settingsPageServices.settingsPageResponse(user.getEmail()));
        return payload;
    }

    private Object taxProfilePayload(String email) {
        return taxProfilePageServices.taxProfilePagePayload(email);
    }

    private Object parseJsonOrRaw(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return value;
        }
    }

    private List<Map<String, Object>> memoryPayload(Integer userId) {
        return agentMemoryService.getRelevantMemories(userId).stream()
                .map(memory -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", memory.getId());
                    payload.put("type", memory.getMemoryType());
                    payload.put("content", memory.getContent());
                    payload.put("confidence", memory.getConfidence() == null ? 1.0d : memory.getConfidence());
                    payload.put("updatedAt", memory.getUpdatedAt() == null ? null : memory.getUpdatedAt().toString());
                    return payload;
                })
                .toList();
    }

    private SessionContextPayload sessionContextPayload(List<AgentSessionMessage> sessionMessages) {
        if (sessionMessages == null || sessionMessages.isEmpty()) {
            return new SessionContextPayload(List.of(), Map.of());
        }

        Map<Integer, ReceiptExtraction> extractionsByFileId = extractionsByFileId(sessionMessages);
        Map<String, Object> fileContext = fileContextPayload(sessionMessages, extractionsByFileId);
        int startIndex = Math.max(0, sessionMessages.size() - 12);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (int index = startIndex; index < sessionMessages.size(); index++) {
            AgentSessionMessage message = sessionMessages.get(index);
            List<Map<String, Object>> files = attachedFilePayload(message, extractionsByFileId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", message.getId());
            item.put("role", message.getRole());
            item.put("content", sessionMemoryContent(message, files));
            item.put("createdAt", message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
            item.put("files", files);
            payload.add(item);
        }
        return new SessionContextPayload(payload, fileContext);
    }

    private Map<String, Object> fileContextPayload(
            List<AgentSessionMessage> sessionMessages,
            Map<Integer, ReceiptExtraction> extractionsByFileId) {
        List<Map<String, Object>> files = sessionMessages.stream()
                .flatMap(message -> attachedFilePayload(message, extractionsByFileId).stream())
                .distinct()
                .toList();

        if (files.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uploadedFiles", files);
        return payload;
    }

    private String sessionMemoryContent(AgentSessionMessage message, List<Map<String, Object>> files) {
        String content = message.getContent() == null ? "" : message.getContent();
        if (files == null || files.isEmpty()) {
            return content;
        }

        try {
            return content + "\n\nAttached uploaded file context:\n" + objectMapper.writeValueAsString(files);
        } catch (Exception ex) {
            return content + "\n\nAttached uploaded file count: " + files.size();
        }
    }

    private List<Map<String, Object>> attachedFilePayload(
            AgentSessionMessage message,
            Map<Integer, ReceiptExtraction> extractionsByFileId) {
        if (message.getFiles() == null || message.getFiles().isEmpty()) {
            return List.of();
        }

        return message.getFiles().stream()
                .map(attachment -> filePayload(attachment, extractionsByFileId))
                .toList();
    }

    private Map<String, Object> filePayload(
            AgentSessionMessageFile attachment,
            Map<Integer, ReceiptExtraction> extractionsByFileId) {
        UploadedFile file = attachment.getUploadedFile();
        if (file == null) {
            return Map.of();
        }

        ReceiptExtraction extraction = extractionsByFileId.get(file.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", file.getId());
        payload.put("fileName", file.getFileName());
        payload.put("contentType", file.getContentType());
        payload.put("documentType", file.getDocumentType() == null ? null : file.getDocumentType().name());
        payload.put("status", file.getStatus());
        payload.put("uploadedAt", file.getUploadedAt() == null ? null : file.getUploadedAt().toString());
        payload.put("metadata", metadataPayload(file.getMetadata()));
        payload.put("uploadRequest", attachment.getUploadRequest());
        payload.put("extractedData", parseJsonOrRaw(attachment.getExtractedData()));

        if (extraction != null) {
            payload.put("extractedReceipt", extractionPayload(extraction));
        }

        return payload;
    }

    private List<Map<String, Object>> taxDocumentContextPayload(String email) {
        return fileRepo.findAllByUser_EmailAndDocumentTypeOrderByUploadedAtDesc(email, DocumentType.TAX_DOCUMENT).stream()
                .limit(12)
                .map(file -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", file.getId());
                    payload.put("fileName", file.getFileName());
                    payload.put("contentType", file.getContentType());
                    payload.put("status", file.getStatus());
                    payload.put("uploadedAt", file.getUploadedAt() == null ? null : file.getUploadedAt().toString());
                    payload.put("metadata", metadataPayload(file.getMetadata()));
                    payload.put("analysis", parseJsonOrRaw(file.getAiSummary()));
                    payload.put("extractedData", parseJsonOrRaw(file.getAiExtractedData()));
                    payload.put("aiProcessedAt", file.getAiProcessedAt() == null ? null : file.getAiProcessedAt().toString());
                    return payload;
                })
                .toList();
    }
    private Map<String, Object> metadataPayload(List<String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        for (String entry : metadata) {
            int separator = entry == null ? -1 : entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            payload.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        return payload;
    }

    private Map<String, Object> extractionPayload(ReceiptExtraction extraction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchant", extraction.getMerchant());
        payload.put("date", extraction.getReceiptDate());
        payload.put("subtotal", extraction.getSubtotal());
        payload.put("tax", extraction.getTax());
        payload.put("total", extraction.getTotal());
        payload.put("currency", extraction.getCurrency());
        payload.put("suggestedCategory", extraction.getSuggestedCategory());
        payload.put("possibleTaxRelevant", extraction.getPossibleTaxRelevant());
        payload.put("confidence", extraction.getConfidence());
        payload.put("items", extraction.getItems() == null
                ? List.of()
                : extraction.getItems().stream()
                        .map(this::extractionItemPayload)
                        .toList());
        return payload;
    }

    private Map<String, Object> extractionItemPayload(ReceiptExtractionItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", item.getName());
        payload.put("amount", item.getAmount());
        return payload;
    }

    private Map<Integer, ReceiptExtraction> extractionsByFileId(List<AgentSessionMessage> sessionMessages) {
        List<Integer> fileIds = sessionMessages.stream()
                .filter(message -> message.getFiles() != null)
                .flatMap(message -> message.getFiles().stream())
                .map(AgentSessionMessageFile::getUploadedFile)
                .filter(file -> file != null)
                .map(UploadedFile::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (fileIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, ReceiptExtraction> extractions = new HashMap<>();
        for (ReceiptExtraction extraction : receiptExtractionRepo.findAllByUploadedFile_IdIn(fileIds)) {
            if (extraction.getUploadedFile() != null && extraction.getUploadedFile().getId() != null) {
                extractions.put(extraction.getUploadedFile().getId(), extraction);
            }
        }
        return extractions;
    }

    private String aiUrl(String path) {
        if (path == null || path.isBlank()) {
            return aiServiceBaseUrl;
        }
        return path.startsWith("/") ? aiServiceBaseUrl + path : aiServiceBaseUrl + "/" + path;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank()
                ? "http://ai-layer:8000"
                : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}




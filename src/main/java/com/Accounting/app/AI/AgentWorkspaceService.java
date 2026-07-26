package com.Accounting.app.AI;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.Accounting.app.AI.dto.AgentMessageRequest;
import com.Accounting.app.AI.dto.AgentMessagesResponse;
import com.Accounting.app.AI.dto.AgentSessionDetailResponse;
import com.Accounting.app.AI.dto.AgentSessionsResponse;
import com.Accounting.app.AI.dto.AgentFilesResponse;
import com.Accounting.app.AI.dto.AgentFolderDto;
import com.Accounting.app.AI.dto.AgentFoldersResponse;
import com.Accounting.app.AI.dto.AgentPdfReportResponse;
import com.Accounting.app.AI.dto.AgentUploadedFileDto;
import com.Accounting.app.AI.dto.CreateAgentFolderRequest;
import com.Accounting.app.AI.dto.MessageDto;
import com.Accounting.app.AI.dto.SessionDetail;
import com.Accounting.app.AI.dto.SessionSummary;
import com.Accounting.app.AI.dto.UpdateAgentSessionRequest;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.files.DocumentType;
import com.Accounting.app.files.FileRepo;
import com.Accounting.app.files.FileUploadService;
import com.Accounting.app.files.ReceiptExtraction;
import com.Accounting.app.files.ReceiptExtractionItem;
import com.Accounting.app.files.ReceiptExtractionRepo;
import com.Accounting.app.files.UploadedFile;
import com.Accounting.app.files.dto.UploadedFileDto;
import com.Accounting.app.settings.SettingsPageServices;
import com.Accounting.app.settings.dto.SettingsPageResponse;
import com.Accounting.app.tax.TaxProfilePageServices;
import com.Accounting.app.tax.dto.TaxProfilePageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AgentWorkspaceService {
    private final AIservice aiService;
    private final AgentFolderRepo agentFolderRepo;
    private final AgentSessionRepo agentSessionRepo;
    private final AgentSessionMessageRepo agentSessionMessageRepo;
    private final FileUploadService fileUploadService;
    private final FileRepo fileRepo;
    private final ReceiptExtractionRepo receiptExtractionRepo;
    private final TaxProfilePageServices taxProfilePageServices;
    private final SettingsPageServices settingsPageServices;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentWorkspaceService(
            AIservice aiService,
            AgentFolderRepo agentFolderRepo,
            AgentSessionRepo agentSessionRepo,
            AgentSessionMessageRepo agentSessionMessageRepo,
            FileUploadService fileUploadService,
            FileRepo fileRepo,
            ReceiptExtractionRepo receiptExtractionRepo,
            TaxProfilePageServices taxProfilePageServices,
            SettingsPageServices settingsPageServices,
            PlatformTransactionManager transactionManager) {
        this.aiService = aiService;
        this.agentFolderRepo = agentFolderRepo;
        this.agentSessionRepo = agentSessionRepo;
        this.agentSessionMessageRepo = agentSessionMessageRepo;
        this.fileUploadService = fileUploadService;
        this.fileRepo = fileRepo;
        this.receiptExtractionRepo = receiptExtractionRepo;
        this.taxProfilePageServices = taxProfilePageServices;
        this.settingsPageServices = settingsPageServices;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public AgentSessionsResponse sessions(String email) {
        List<SessionSummary> sessions = agentSessionRepo.findAllByUserEmailOrderByUpdatedAtDesc(email).stream()
                .map(this::toSummary)
                .toList();
        return new AgentSessionsResponse(sessions, folders(email).folders());
    }

    @Transactional(readOnly = true)
    public AgentSessionDetailResponse session(String email, String sessionId) {
        AgentSession session = agentSessionRepo.findByIdAndUserEmail(sessionId, email)
                .orElseThrow(() -> new InvalidInputException("Agent session not found"));
        return new AgentSessionDetailResponse(toDetail(session));
    }

    @Transactional
    public AgentSessionDetailResponse createSession(String email) {
        AgentSession session = new AgentSession();
        session.setId(this.sessionId());
        session.setUserEmail(email);
        session.setTitle("New chat");
        session.setPreview("Ask the accounting agent a question.");
        session.setUpdatedAt(Instant.now());
        AgentSession saved = agentSessionRepo.save(session);
        return new AgentSessionDetailResponse(toDetail(saved));
    }

    public AgentMessagesResponse sendMessage(String email, AgentMessageRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isBlank()) {
            throw new InvalidInputException("Message is required");
        }

        AgentSession session = resolveSession(email, request.sessionId(), message);
        Instant now = Instant.now();
        String userMessageId = messageId();
        String agentMessageId = messageId();
        List<AgentSessionMessage> sessionContext = sessionContext(session, userMessageId, message, now);
        String agentReply = generateReply(email, message, sessionContext);

        return transactionTemplate.execute(status -> persistMessageExchange(
                session,
                message,
                agentReply,
                now,
                userMessageId,
                agentMessageId));
    }

    public AgentFilesResponse uploadFiles(
            String email,
            String sessionId,
            MultipartFile[] files,
            DocumentType documentType,
            String prompt) {
        if (files == null || files.length == 0) {
            throw new InvalidInputException("At least one file is required");
        }
        fileUploadService.validateUploadBatch(files);

        String uploadPrompt = prompt == null || prompt.isBlank()
                ? "Analyze this uploaded file and explain the important accounting or tax details."
                : prompt.trim();
        String fileSummary = fileSummary(files);
        AgentSession session = resolveSession(email, sessionId, fileSummary);
        String resolvedSessionId = session.getId();

        List<UploadedFileDto> uploadedFiles = new ArrayList<>();
        List<AgentFileAnalysisResult> agentAnalyses = new ArrayList<>();

        for (MultipartFile file : files) {
            DocumentType resolvedDocumentType = documentType == null ? inferDocumentType(file) : documentType;
            UploadedFileDto uploaded = fileUploadService.uploadAgentFile(
                    file,
                    email,
                    resolvedDocumentType,
                    java.util.Map.of("agentSessionId", resolvedSessionId));
            uploadedFiles.add(uploaded);
            agentAnalyses.add(aiService.analyzeAgentFile(email, file, uploadPrompt, resolvedSessionId));
        }

        return transactionTemplate.execute(status -> persistFileAnalysis(
                session,
                uploadedFiles,
                fileSummary,
                uploadPrompt,
                agentAnalyses));
    }

    @Transactional(readOnly = true)
    public AgentPdfReportResponse createPdfReport(String email, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new InvalidInputException("Message id is required");
        }

        AgentSessionMessage message = agentSessionMessageRepo.findByIdAndSession_UserEmail(messageId, email)
                .orElseThrow(() -> new InvalidInputException("Agent message not found"));
        if (!"agent".equalsIgnoreCase(message.getRole())) {
            throw new InvalidInputException("Only agent responses can be exported as PDF");
        }

        return aiService.createAgentPdfReport(email, reportPayloadFor(message));
    }

    public byte[] downloadPdfReport(String email, String fileName) {
        return aiService.downloadAgentPdfReport(email, fileName);
    }

    @Transactional(readOnly = true)
    public AgentFoldersResponse folders(String email) {
        List<AgentFolderDto> folders = agentFolderRepo.findAllByUserEmailOrderByCreatedAtDesc(email).stream()
                .map(this::toFolder)
                .toList();
        return new AgentFoldersResponse(folders);
    }

    @Transactional
    public AgentFolderDto createFolder(String email, CreateAgentFolderRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new InvalidInputException("Folder name is required");
        }

        AgentFolder folder = new AgentFolder();
        folder.setId(folderId());
        folder.setUserEmail(email);
        folder.setName(name);
        folder.setColor(normalizeFolderColor(request.color()));
        folder.setCreatedAt(Instant.now());
        return toFolder(agentFolderRepo.save(folder));
    }

    @Transactional
    public void updateSession(String email, String sessionId, UpdateAgentSessionRequest request) {
        AgentSession session = agentSessionRepo.findByIdAndUserEmail(sessionId, email)
                .orElseThrow(() -> new InvalidInputException("Agent session not found"));

        if (request.folderId() == null || request.folderId().isBlank()) {
            session.setFolder(null);
        } else {
            AgentFolder folder = agentFolderRepo.findByIdAndUserEmail(request.folderId(), email)
                    .orElseThrow(() -> new InvalidInputException("Agent folder not found"));
            session.setFolder(folder);
        }

        agentSessionRepo.save(session);
    }

    @Transactional
    public void deleteSession(String email, String sessionId) {
        AgentSession session = agentSessionRepo.findByIdAndUserEmail(sessionId, email)
                .orElseThrow(() -> new InvalidInputException("Agent session not found"));
        agentSessionRepo.delete(session);
    }

    private AgentSession resolveSession(String email, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            AgentSession session = new AgentSession();
            session.setId(this.sessionId());
            session.setUserEmail(email);
            session.setTitle(titleFor(message));
            session.setPreview("");
            session.setUpdatedAt(Instant.now());
            return session;
        }

        return agentSessionRepo.findByIdAndUserEmail(sessionId, email)
                .orElseThrow(() -> new InvalidInputException("Agent session not found"));
    }

    private List<AgentSessionMessage> sessionContext(AgentSession session, String messageId, String message, Instant createdAt) {
        List<AgentSessionMessage> context = orderedUniqueMessages(session);

        AgentSessionMessage currentMessage = new AgentSessionMessage();
        currentMessage.setId(messageId);
        currentMessage.setRole("user");
        currentMessage.setContent(message);
        currentMessage.setCreatedAt(createdAt);

        java.util.ArrayList<AgentSessionMessage> combined = new java.util.ArrayList<>(context);
        combined.add(currentMessage);
        return combined;
    }

    private SessionSummary toSummary(AgentSession session) {
        return new SessionSummary(
                session.getId(),
                session.getTitle(),
                fallback(session.getPreview(), ""),
                session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString(),
                session.getFolder() == null ? null : session.getFolder().getId(),
                session.getFolder() == null ? null : session.getFolder().getName());
    }

    private SessionDetail toDetail(AgentSession session) {
        return new SessionDetail(
                session.getId(),
                session.getTitle(),
                fallback(session.getPreview(), ""),
                session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString(),
                session.getFolder() == null ? null : session.getFolder().getId(),
                session.getFolder() == null ? null : session.getFolder().getName(),
                orderedUniqueMessages(session).stream()
                        .map(this::toMessage)
                        .toList());
    }

    private AgentMessagesResponse persistMessageExchange(
            AgentSession session,
            String message,
            String agentReply,
            Instant userCreatedAt,
            String userMessageId,
            String agentMessageId) {
        AgentSessionMessage userMessage = new AgentSessionMessage();
        userMessage.setId(userMessageId);
        userMessage.setSession(session);
        userMessage.setRole("user");
        userMessage.setContent(message);
        userMessage.setCreatedAt(userCreatedAt);
        session.getMessages().add(userMessage);

        AgentSessionMessage agentMessage = new AgentSessionMessage();
        agentMessage.setId(agentMessageId);
        agentMessage.setSession(session);
        agentMessage.setRole("agent");
        agentMessage.setContent(agentReply);
        agentMessage.setCreatedAt(Instant.now());
        session.getMessages().add(agentMessage);

        session.setTitle(titleFor(message));
        session.setPreview(previewFor(agentReply));
        session.setUpdatedAt(agentMessage.getCreatedAt());

        AgentSession saved = agentSessionRepo.save(session);
        return new AgentMessagesResponse(
                toSummary(saved),
                orderedUniqueMessages(saved).stream()
                        .map(this::toMessage)
                        .toList());
    }

    private AgentFilesResponse persistFileAnalysis(
            AgentSession session,
            List<UploadedFileDto> uploadedFiles,
            String userContent,
            String uploadRequest,
            List<AgentFileAnalysisResult> agentAnalyses) {
        Instant now = Instant.now();
        String uploadMessageContent = uploadMessageContent(userContent, uploadRequest);

        AgentSessionMessage userMessage = new AgentSessionMessage();
        userMessage.setId(messageId());
        userMessage.setSession(session);
        userMessage.setRole("user");
        userMessage.setContent(uploadMessageContent);
        userMessage.setCreatedAt(now);
        userMessage.setUploadRequest(uploadRequest);
        userMessage.setFiles(uploadedMessageFiles(userMessage, uploadedFiles, uploadRequest, agentAnalyses, now));
        session.getMessages().add(userMessage);

        Instant latestMessageAt = now;
        for (AgentFileAnalysisResult agentAnalysis : agentAnalyses) {
            String agentReply = agentAnalysis == null ? null : agentAnalysis.answerJson();
            AgentSessionMessage agentMessage = new AgentSessionMessage();
            agentMessage.setId(messageId());
            agentMessage.setSession(session);
            agentMessage.setRole("agent");
            agentMessage.setContent(agentReply == null || agentReply.isBlank()
                    ? fileAnalysisFallback()
                    : agentReply.trim());
            agentMessage.setCreatedAt(Instant.now());
            latestMessageAt = agentMessage.getCreatedAt();
            session.getMessages().add(agentMessage);
        }

        session.setTitle(session.getTitle() == null || session.getTitle().isBlank() || "New chat".equals(session.getTitle())
                ? "File analysis"
                : session.getTitle());
        String latestReply = latestAnalysisReply(agentAnalyses);
        session.setPreview(latestReply == null ? userContent : previewFor(latestReply));
        session.setUpdatedAt(latestMessageAt);

        AgentSession saved = agentSessionRepo.save(session);
        List<AgentSessionMessage> savedMessages = orderedUniqueMessages(saved);
        List<AgentUploadedFileDto> responseFiles = savedMessages.stream()
                .filter(message -> userMessage.getId().equals(message.getId()))
                .findFirst()
                .map(this::messageFiles)
                .orElseGet(() -> uploadedFiles.stream().map(this::toAgentUploadedFile).toList());
        return new AgentFilesResponse(
                toSummary(saved),
                responseFiles,
                savedMessages.stream()
                        .map(this::toMessage)
                        .toList());
    }

    private List<AgentSessionMessage> orderedUniqueMessages(AgentSession session) {
        if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
            return List.of();
        }

        Map<String, AgentSessionMessage> messagesById = new LinkedHashMap<>();
        session.getMessages().stream()
                .filter(message -> message != null && message.getId() != null)
                .sorted(messageOrder())
                .forEach(message -> messagesById.putIfAbsent(message.getId(), message));
        return new ArrayList<>(messagesById.values());
    }

    private Comparator<AgentSessionMessage> messageOrder() {
        return Comparator.comparing(
                AgentSessionMessage::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgentSessionMessage::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private AgentFolderDto toFolder(AgentFolder folder) {
        return new AgentFolderDto(
                folder.getId(),
                folder.getName(),
                folder.getColor(),
                folder.getCreatedAt() == null ? null : folder.getCreatedAt().toString());
    }

    private MessageDto toMessage(AgentSessionMessage message) {
        JsonNode response = parseObject(message.getContent());
        if (response != null) {
            return new MessageDto(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getCreatedAt() == null ? null : message.getCreatedAt().toString(),
                    message.getUploadRequest(),
                    textOrNull(response, "responseType"),
                    textOrNull(response, "answerType"),
                    textOrNull(response, "title"),
                    textOrNull(response, "summary"),
                    nodeOrNull(response, "sections"),
                    nodeOrNull(response, "blocks"),
                    nodeOrNull(response, "taxPlan"),
                    nodeOrNull(response, "meta"),
                    textOrNull(response, "reasoningSummary"),
                    textOrNull(response, "confidence"),
                    nodeOrNull(response, "missingInformation"),
                    nodeOrNull(response, "recommendedNextSteps"),
                    nodeOrNull(response, "sources"),
                    nodeOrNull(response, "usedContext"),
                    messageFiles(message));
        }

        return new MessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt() == null ? null : message.getCreatedAt().toString(),
                message.getUploadRequest(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                messageFiles(message));
    }

    private JsonNode reportPayloadFor(AgentSessionMessage message) {
        JsonNode parsed = parseObject(message.getContent());
        if (parsed != null) {
            return parsed;
        }

        String content = fallback(message.getContent(), "");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Agent Response");
        payload.put("summary", content);

        ArrayNode blocks = payload.putArray("blocks");
        ObjectNode block = blocks.addObject();
        block.put("type", "paragraph");
        block.put("content", content);
        block.putArray("items");
        block.putArray("columns");
        block.putArray("rows");
        return payload;
    }

    private JsonNode parseObject(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node.isObject() ? node : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode nodeOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value;
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }


    private String generateReply(String email, String message, List<AgentSessionMessage> sessionMessages) {
        String pythonReply = aiService.askAgentQuestion(email, message, sessionMessages);
        if (pythonReply != null && !pythonReply.isBlank()) {
            return pythonReply.trim();
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("coffee")) {
            return coffeeReply(email);
        }
        if (normalized.contains("tax") && (normalized.contains("house") || normalized.contains("home"))) {
            return taxPlanningReply(email);
        }
        if (normalized.contains("tax")) {
            return generalTaxReply(email);
        }
        return genericReply(email, message);
    }

    private String coffeeReply(String email) {
        List<ReceiptExtraction> receipts = receiptExtractionRepo.findAllByUploadedFile_User_Email(email);
        List<ReceiptExtraction> matches = receipts.stream()
                .filter(this::isCoffeeReceipt)
                .toList();
        BigDecimal total = matches.stream()
                .map(ReceiptExtraction::getTotal)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (matches.isEmpty()) {
            return "I did not find any coffee-related receipts in your current uploaded files. "
                    + "If you have more receipts to scan, upload them and I can review them in this session.";
        }

        String topMerchant = matches.stream()
                .map(ReceiptExtraction::getMerchant)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(value -> value, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("coffee merchant");

        return "I found " + matches.size() + " coffee-related receipts. Total spend was $"
                + total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + ". " + topMerchant + " was the most common merchant in the current uploaded receipt data.";
    }

    private boolean isCoffeeReceipt(ReceiptExtraction extraction) {
        String haystack = (fallback(extraction.getMerchant(), "") + " "
                + fallback(extraction.getSuggestedCategory(), "") + " "
                + extraction.getItems().stream()
                        .map(ReceiptExtractionItem::getName)
                        .filter(item -> item != null)
                        .reduce("", (left, right) -> left + " " + right)).toLowerCase(Locale.ROOT);
        return haystack.contains("coffee")
                || haystack.contains("starbucks")
                || haystack.contains("cafe")
                || haystack.contains("espresso");
    }

    private String taxPlanningReply(String email) {
        TaxProfilePageResponse taxProfile = taxProfilePageServices.taxProfilePageResponse(email);
        SettingsPageResponse settings = settingsPageServices.settingsPageResponse(email);
        String currency = taxProfile.getFilingProfile() != null
                ? fallback(taxProfile.getFilingProfile().getBaseCurrency(), "CAD")
                : "CAD";
        String residence = taxProfile.getFilingProfile() != null
                ? fallback(taxProfile.getFilingProfile().getTaxResidence(), "Canada")
                : "Canada";
        String businessName = settings.getBusiness() != null
                ? fallback(settings.getBusiness().getName(), "your account")
                : "your account";

        return "Based on your current account and tax profile for " + businessName + ", the strongest long-term "
                + "strategy is to prioritize tax-advantaged savings first, keep your home fund separate from "
                + "day-to-day operating cash, and avoid taking unnecessary taxable risk with money you may need soon. "
                + "Your current filing profile is anchored to " + residence + " and " + currency
                + ", so the next step is to decide whether your house timeline is short enough that capital "
                + "preservation matters more than growth. If the purchase window is under 3 years, keep the down "
                + "payment fund liquid and stable. If it is longer, moderate growth becomes more reasonable. "
                + "I would also review deduction usage, contribution room, and recurring expenses so the house plan "
                + "does not crowd out emergency reserves or long-term retirement funding.";
    }

    private String generalTaxReply(String email) {
        TaxProfilePageResponse taxProfile = taxProfilePageServices.taxProfilePageResponse(email);
        int incomeSourceCount = taxProfile.getIncomeSources() == null ? 0 : taxProfile.getIncomeSources().size();
        int deductionCount = taxProfile.getDeductionCategories() == null ? 0 : taxProfile.getDeductionCategories().size();

        return "Using your current tax profile, I would focus first on separating taxable income sources, tightening "
                + "documentation for deductions, and reviewing any estimated payment deadlines before they become urgent. "
                + "Right now I can see " + incomeSourceCount + " income source entries and " + deductionCount
                + " deduction categories in the configured profile. The next practical step is to confirm which of those "
                + "items are active, which ones are missing documentation, and where contribution or deduction planning "
                + "can reduce future tax friction.";
    }

    private String genericReply(String email, String message) {
        SettingsPageResponse settings = settingsPageServices.settingsPageResponse(email);
        String businessName = settings.getBusiness() != null
                ? fallback(settings.getBusiness().getName(), "your account")
                : "your account";
        return "I stored this request in your workspace and reviewed the current information available for "
                + businessName + ". The next step is to narrow the objective, time window, and whether you want the "
                + "analysis to focus on receipts, transactions, tax planning, or cash management. Request received: "
                + message;
    }

    private String titleFor(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("coffee")) {
            return "Coffee spending review";
        }
        if (normalized.contains("tax") && (normalized.contains("house") || normalized.contains("home"))) {
            return "Tax planning for house savings";
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 48) {
            return cleaned;
        }
        return cleaned.substring(0, 48).trim() + "...";
    }

    private String previewFor(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 96) {
            return cleaned;
        }
        return cleaned.substring(0, 96).trim() + "...";
    }

    private String latestAnalysisReply(List<AgentFileAnalysisResult> agentAnalyses) {
        if (agentAnalyses == null || agentAnalyses.isEmpty()) {
            return null;
        }

        for (int index = agentAnalyses.size() - 1; index >= 0; index--) {
            AgentFileAnalysisResult analysis = agentAnalyses.get(index);
            if (analysis != null && analysis.answerJson() != null && !analysis.answerJson().isBlank()) {
                return analysis.answerJson();
            }
        }
        return null;
    }

    private String fileSummary(MultipartFile[] files) {
        String names = java.util.Arrays.stream(files)
                .map(file -> file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                        ? "uploaded file"
                        : file.getOriginalFilename())
                .collect(java.util.stream.Collectors.joining(", "));
        return "Uploaded file" + (files.length == 1 ? "" : "s") + " for analysis: " + names;
    }

    private String uploadMessageContent(String fileSummary, String uploadRequest) {
        if (uploadRequest == null || uploadRequest.isBlank()) {
            return fileSummary;
        }
        return fileSummary + "\nRequest: " + uploadRequest;
    }

    private DocumentType inferDocumentType(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);

        if (contentType.contains("spreadsheet")
                || contentType.contains("excel")
                || "text/csv".equals(contentType)
                || fileName.endsWith(".csv")
                || fileName.endsWith(".xls")
                || fileName.endsWith(".xlsx")) {
            return DocumentType.SPREADSHEET;
        }

        if ("application/pdf".equals(contentType)
                || contentType.startsWith("text/")
                || fileName.endsWith(".pdf")
                || fileName.endsWith(".txt")) {
            return DocumentType.TAX_DOCUMENT;
        }

        return DocumentType.OTHER;
    }

    private AgentUploadedFileDto toAgentUploadedFile(UploadedFileDto file) {
        String fileType = switch (fallback(file.getFileType(), "other")) {
            case "spreadsheet" -> "spreadsheet";
            case "image" -> "image";
            case "pdf", "document" -> "document";
            default -> file.getDocumentType() == DocumentType.RECEIPT ? "receipt" : "other";
        };
        String status = "needs review".equals(file.getStatus()) ? "failed" : "processed";

        return new AgentUploadedFileDto(
                file.getId() == null ? null : file.getId().toString(),
                file.getFileName(),
                fileType,
                file.getContentType(),
                file.getSizeBytes(),
                status,
                file.getUploadedAt() == null ? null : file.getUploadedAt().toString(),
                null,
                null);
    }

    private Set<AgentSessionMessageFile> uploadedMessageFiles(
            AgentSessionMessage message,
            List<UploadedFileDto> uploadedFiles,
            String uploadRequest,
            List<AgentFileAnalysisResult> agentAnalyses,
            Instant createdAt) {
        List<Integer> fileIds = uploadedFiles.stream()
                .map(UploadedFileDto::getId)
                .filter(id -> id != null)
                .toList();
        if (fileIds.isEmpty()) {
            return Set.of();
        }

        Map<Integer, UploadedFile> filesById = new LinkedHashMap<>();
        fileRepo.findAllById(fileIds).forEach(file -> filesById.put(file.getId(), file));

        Set<AgentSessionMessageFile> attachments = new LinkedHashSet<>();
        for (int index = 0; index < uploadedFiles.size(); index++) {
            UploadedFileDto uploadedFile = uploadedFiles.get(index);
            UploadedFile file = uploadedFile.getId() == null ? null : filesById.get(uploadedFile.getId());
            if (file == null) {
                continue;
            }
            attachments.add(new AgentSessionMessageFile(
                    message,
                    file,
                    uploadRequest,
                    index < agentAnalyses.size() && agentAnalyses.get(index) != null
                            ? agentAnalyses.get(index).extractedDataJson()
                            : null,
                    createdAt));
        }
        return attachments;
    }

    private List<AgentUploadedFileDto> messageFiles(AgentSessionMessage message) {
        if (message.getFiles() == null || message.getFiles().isEmpty()) {
            return List.of();
        }

        return message.getFiles().stream()
                .map(this::toAgentUploadedFile)
                .filter(file -> file != null)
                .toList();
    }

    private AgentUploadedFileDto toAgentUploadedFile(AgentSessionMessageFile attachment) {
        UploadedFile file = attachment.getUploadedFile();
        if (file == null) {
            return null;
        }
        String fileType = switch (fileType(file)) {
            case "spreadsheet" -> "spreadsheet";
            case "image" -> "image";
            case "pdf", "document" -> "document";
            default -> file.getDocumentType() == DocumentType.RECEIPT ? "receipt" : "other";
        };
        String status = "needs review".equals(normalizeStatus(file.getStatus())) ? "failed" : "processed";

        return new AgentUploadedFileDto(
                file.getId() == null ? null : file.getId().toString(),
                file.getFileName(),
                fileType,
                file.getContentType(),
                file.getFileSize(),
                status,
                file.getUploadedAt() == null ? null : file.getUploadedAt().toString(),
                attachment.getUploadRequest(),
                parseJsonOrNull(attachment.getExtractedData()));
    }

    private JsonNode parseJsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String fileType(UploadedFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String fileName = file.getFileName() == null ? "" : file.getFileName().toLowerCase(Locale.ROOT);

        if (file.getDocumentType() == DocumentType.SPREADSHEET
                || contentType.contains("spreadsheet")
                || contentType.contains("excel")
                || "text/csv".equals(contentType)
                || fileName.endsWith(".csv")
                || fileName.endsWith(".xls")
                || fileName.endsWith(".xlsx")) {
            return "spreadsheet";
        }

        if ("application/pdf".equals(contentType) || fileName.endsWith(".pdf")) {
            return "pdf";
        }

        if (contentType.startsWith("text/") || fileName.endsWith(".txt")) {
            return "document";
        }

        if (contentType.startsWith("image/")) {
            return "image";
        }

        return "other";
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String fileAnalysisFallback() {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "title", "File analysis unavailable",
                    "summary", "The file was uploaded, but no analysis response was returned.",
                    "confidence", "low"));
        } catch (Exception ex) {
            return "The file was uploaded, but no analysis response was returned.";
        }
    }

    private String sessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String folderId() {
        return "folder_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalizeFolderColor(String color) {
        if (color == null || color.isBlank()) {
            return "slate";
        }
        return color.trim();
    }

    private String messageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

package com.Accounting.app.files;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.Accounting.app.files.dto.FileUploadResponse;
import com.Accounting.app.files.dto.ReceiptProcessingResponse;
import com.Accounting.app.files.dto.UploadedFileContent;
import com.Accounting.app.files.dto.UploadedFileDto;
import com.Accounting.app.files.dto.UploadedFileDto.ReceiptItemDto;
import com.Accounting.app.files.dto.UploadedFileDto.ReceiptExtractionDto;
import com.Accounting.app.files.storage.S3DocumentStorageService;
import com.Accounting.app.files.storage.StoredS3Object;
import com.Accounting.app.files.security.UploadSecurityValidator;
import com.Accounting.app.files.security.UploadSecurityValidator.ValidatedUpload;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.transactions.Transaction;
import com.Accounting.app.AI.AIservice;
import com.Accounting.app.AI.AgentFileAnalysisResult;
import com.Accounting.app.auth.User;
import com.Accounting.app.transactions.TransactionsRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Accounting.app.auth.UserRepo;

@Service
public class FileUploadService {
    private static final String UPLOADED_STATUS = "uploaded";
    private static final String PROCESSING_STATUS = "processing";
    private static final String EXTRACTED_STATUS = "extracted";
    private static final String NEEDS_REVIEW_STATUS = "needs review";
    private final UserRepo userRepo;
    private final FileRepo fileRepo;
    private final TransactionsRepo transactionsRepo;
    private final AIservice aiService;
    private final ReceiptExtractionRepo receiptExtractionRepo;
    private final S3DocumentStorageService storageService;
    private final UploadSecurityValidator uploadSecurityValidator;

    public FileUploadService(UserRepo userRepo, FileRepo fileRepo, TransactionsRepo transactionsRepo,
            AIservice aiService, ReceiptExtractionRepo receiptExtractionRepo,
            S3DocumentStorageService storageService,
            UploadSecurityValidator uploadSecurityValidator) {
        this.userRepo = userRepo;
        this.fileRepo = fileRepo;
        this.transactionsRepo = transactionsRepo;
        this.aiService = aiService;
        this.receiptExtractionRepo = receiptExtractionRepo;
        this.storageService = storageService;
        this.uploadSecurityValidator = uploadSecurityValidator;
    }

    public UploadedFileDto uploadFile(
            MultipartFile file,
            String email,
            DocumentType documentType,
            Integer transactionId,
            Map<String, String> providedMetadata) {
        return uploadFile(file, email, documentType, transactionId, providedMetadata, true);
    }

    public UploadedFileDto uploadAgentFile(
            MultipartFile file,
            String email,
            DocumentType documentType,
            Map<String, String> providedMetadata) {
        return uploadFile(file, email, documentType, null, providedMetadata, false);
    }

    private UploadedFileDto uploadFile(
            MultipartFile file,
            String email,
            DocumentType documentType,
            Integer transactionId,
            Map<String, String> providedMetadata,
            boolean processReceipt) {
        try {
            User user = userRepo.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));
            Transaction relatedTransaction = transactionId != null
                    ? transactionsRepo.findByTransactionIdAndAccount_User_Email(transactionId, email)
                            .orElseThrow(() -> new InvalidInputException("Transaction not found for user"))
                    : null;

            ValidatedUpload validatedUpload = uploadSecurityValidator.validateDocument(file);
            String contentType = validatedUpload.contentType();
            String originalFilename = validatedUpload.originalFilename();

            StoredS3Object storedObject = storageService.store(
                    file,
                    user.getUserId(),
                    documentType,
                    originalFilename,
                    contentType);
            UploadedFile uploadedFile = new UploadedFile();
            uploadedFile.setUser(user);
            uploadedFile.setFileName(originalFilename);
            uploadedFile.setFilePath(storedObject.location());
            uploadedFile.setContentType(contentType);
            uploadedFile.setFileSize(file.getSize());
            uploadedFile.setUploadedAt(LocalDateTime.now());
            uploadedFile.setStatus(initialStoredStatus(documentType));
            uploadedFile.setDocumentType(documentType);
            uploadedFile.setRelatedTransaction(relatedTransaction);
            uploadedFile.setMetadata(buildMetadata(file, originalFilename, providedMetadata));
            UploadedFile savedFile = fileRepo.save(uploadedFile);

            if (processReceipt && documentType == DocumentType.RECEIPT) {
                String aiResult = aiService.processReceipt(
                        file,
                        user.getUserId(),
                        savedFile.getId());

                ObjectMapper mapper = new ObjectMapper();

                ReceiptProcessingResponse response = mapper.readValue(aiResult, ReceiptProcessingResponse.class);

                savedFile.setStatus("extracted");
                savedFile = fileRepo.save(savedFile);
                ReceiptExtraction extraction = new ReceiptExtraction(response.getExtracted().getMerchant(),
                        normalizeReceiptDate(response.getExtracted().getReceiptDate()), response.getExtracted().getSubtotal(),
                        response.getExtracted().getTax(), response.getExtracted().getTotal(),
                        response.getExtracted().getCurrency(), response.getExtracted().getSuggestedCategory(),
                        response.getExtracted().resolvePossibleTaxRelevant(), response.getExtracted().getConfidence(),
                        savedFile);
                extraction.setItems(extractionItems(response.getExtracted().getItems(), extraction));

                receiptExtractionRepo.save(extraction);

            }

            if (processReceipt && documentType == DocumentType.TAX_DOCUMENT) {
                savedFile = processTaxDocument(file, email, savedFile, providedMetadata);
            }

            return toDto(savedFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    public FileUploadResponse uploadFiles(
            MultipartFile[] files,
            String email,
            DocumentType documentType,
            Integer transactionId,
            Map<String, String> providedMetadata) {
        uploadSecurityValidator.validateBatch(files);

        List<UploadedFileDto> uploadedFiles = Stream.of(files)
                .map(file -> uploadFile(file, email, documentType, transactionId, providedMetadata))
                .toList();

        return new FileUploadResponse(uploadedFiles);
    }

    public void validateUploadBatch(MultipartFile[] files) {
        uploadSecurityValidator.validateBatch(files);
    }

    private UploadedFile processTaxDocument(
            MultipartFile file,
            String email,
            UploadedFile savedFile,
            Map<String, String> providedMetadata) {
        savedFile.setStatus(PROCESSING_STATUS);
        savedFile = fileRepo.save(savedFile);

        AgentFileAnalysisResult analysis = aiService.analyzeAgentFile(
                email,
                file,
                taxDocumentAnalysisPrompt(savedFile, providedMetadata),
                "tax-document-" + savedFile.getId());

        savedFile.setAiSummary(blankToNull(analysis.answerJson()));
        savedFile.setAiExtractedData(blankToNull(analysis.extractedDataJson()));
        savedFile.setAiProcessedAt(LocalDateTime.now());
        savedFile.setStatus(hasUsableAiAnalysis(analysis) ? EXTRACTED_STATUS : NEEDS_REVIEW_STATUS);
        return fileRepo.save(savedFile);
    }

    private String taxDocumentAnalysisPrompt(UploadedFile file, Map<String, String> providedMetadata) {
        String taxYear = providedMetadata == null ? null : providedMetadata.get("taxYear");
        String category = providedMetadata == null ? null : providedMetadata.get("category");
        return "Analyze this uploaded tax document for accounting and tax planning. "
                + "Extract the tax year, document type/category, issuer/source, important amounts, dates, names, "
                + "tax relevance, filing readiness, missing information, and recommended next steps. "
                + "Return structured fileContext JSON that can be reused by the tax agent in later planning. "
                + "User-provided tax year: " + fallback(taxYear, "unknown") + "; category: "
                + fallback(category, "unknown") + "; file name: " + fallback(file.getFileName(), "unknown") + ".";
    }

    private boolean hasUsableAiAnalysis(AgentFileAnalysisResult analysis) {
        if (analysis == null) {
            return false;
        }
        String extractedData = blankToNull(analysis.extractedDataJson());
        String answer = blankToNull(analysis.answerJson());
        return extractedData != null || (answer != null && !answer.contains("Agent service issue"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> buildMetadata(
            MultipartFile file,
            String originalFilename,
            Map<String, String> providedMetadata) {
        List<String> metadata = new ArrayList<>();
        metadata.add("originalFilename=" + originalFilename);

        if (file.getContentType() != null) {
            metadata.add("contentType=" + file.getContentType());
        }

        String extension = extension(originalFilename);
        if (!extension.isBlank()) {
            metadata.add("extension=" + extension);
        }

        if (providedMetadata != null) {
            providedMetadata.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    metadata.add(key + "=" + value);
                }
            });
        }

        return metadata;
    }

    private UploadedFileDto toDto(UploadedFile uploadedFile) {
        ReceiptExtraction extraction = receiptExtractionRepo.findByUploadedFile_Id(uploadedFile.getId()).orElse(null);
        return toDto(uploadedFile, extraction);
    }

    private UploadedFileDto toDto(UploadedFile uploadedFile, ReceiptExtraction extraction) {
        Integer relatedTransactionId = uploadedFile.getRelatedTransaction() != null
                ? uploadedFile.getRelatedTransaction().getId()
                : null;
        String storedFileName = storedFileName(uploadedFile);
        String fileUrl = buildFileUrl(uploadedFile);
        String fileType = determineFileType(uploadedFile);
        Map<String, Object> metadata = metadataMap(uploadedFile.getMetadata(), extraction);
        mergeAiFileMetadata(metadata, uploadedFile);

        return new UploadedFileDto(
                uploadedFile.getId(),
                uploadedFile.getFileName(),
                storedFileName,
                fileUrl,
                buildPreviewUrl(fileType, fileUrl),
                uploadedFile.getContentType(),
                fileType,
                uploadedFile.getFileSize(),
                uploadedFile.getFileSize(),
                uploadedFile.getUploadedAt(),
                determineFrontendStatus(uploadedFile),
                uploadedFile.getDocumentType(),
                relatedTransactionId,
                metadata,
                extractionDto(extraction));
    }

    public UploadedFileDto updateUploadedFileMetadata(
            Integer fileId,
            String email,
            Map<String, String> updates) {
        UploadedFile uploadedFile = fileRepo.findByIdAndUser_Email(fileId, email)
                .orElseThrow(() -> new InvalidInputException("File not found for user"));

        Map<String, Object> currentMetadata = metadataMap(uploadedFile.getMetadata(), null);
        if (updates != null) {
            updates.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank() && !"status".equals(key)) {
                    currentMetadata.put(key, value);
                }
            });

            String status = updates.get("status");
            if (status != null && !status.isBlank()) {
                uploadedFile.setStatus(status);
            }
        }

        List<String> nextMetadata = new ArrayList<>();
        currentMetadata.forEach((key, value) -> {
            if (key != null && value != null && !String.valueOf(value).isBlank()) {
                nextMetadata.add(key + "=" + value);
            }
        });
        uploadedFile.setMetadata(nextMetadata);

        return toDto(fileRepo.save(uploadedFile));
    }
    public List<UploadedFileDto> loadUploadedFiles(String email) {
        userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<UploadedFile> uploadedFiles = fileRepo.findAllByUser_Email(email);
        Map<Integer, ReceiptExtraction> extractionsByFileId = extractionsByFileId(uploadedFiles);

        return uploadedFiles.stream()
                .sorted(Comparator.comparing(UploadedFile::getUploadedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(uploadedFile -> toDto(uploadedFile, extractionsByFileId.get(uploadedFile.getId())))
                .toList();
    }

    public UploadedFileContent loadUploadedFileContent(Integer fileId, String email) {
        UploadedFile uploadedFile = fileRepo.findByIdAndUser_Email(fileId, email)
                .orElseThrow(() -> new InvalidInputException("File not found for user"));

        return new UploadedFileContent(
                storageService.createPresignedDownloadUri(
                        uploadedFile.getFilePath(),
                        uploadedFile.getFileName()),
                uploadedFile.getFileName(),
                uploadedFile.getContentType());
    }

    private String storedFileName(UploadedFile uploadedFile) {
        if (uploadedFile.getFilePath() == null || uploadedFile.getFilePath().isBlank()) {
            return null;
        }

        if (storageService.isManagedLocation(uploadedFile.getFilePath())) {
            return storageService.storedFileName(uploadedFile.getFilePath());
        }

        try {
            return Paths.get(uploadedFile.getFilePath()).getFileName().toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildFileUrl(UploadedFile uploadedFile) {
        return uploadedFile.getId() == null
                ? null
                : "/api/dashboard/files/" + uploadedFile.getId() + "/content";
    }

    private String buildPreviewUrl(String fileType, String fileUrl) {
        return "image".equals(fileType) ? fileUrl : null;
    }

    private String determineFileType(UploadedFile uploadedFile) {
        if (uploadedFile.getDocumentType() == DocumentType.SPREADSHEET) {
            return "spreadsheet";
        }

        String contentType = normalize(uploadedFile.getContentType());
        String fileName = normalize(uploadedFile.getFileName());
        String extension = extension(fileName);

        if (contentType.contains("spreadsheet")
                || contentType.contains("excel")
                || "text/csv".equals(contentType)
                || "application/csv".equals(contentType)
                || Set.of("csv", "xls", "xlsx").contains(extension)) {
            return "spreadsheet";
        }

        if ("application/pdf".equals(contentType) || "pdf".equals(extension)) {
            return "pdf";
        }

        if (contentType.startsWith("text/") || "txt".equals(extension)) {
            return "document";
        }

        return "image";
    }

    private String determineFrontendStatus(UploadedFile uploadedFile) {
        String normalizedStoredStatus = normalizeStatus(uploadedFile.getStatus());

        if (EXTRACTED_STATUS.equals(normalizedStoredStatus)) {
            return EXTRACTED_STATUS;
        }

        if (PROCESSING_STATUS.equals(normalizedStoredStatus)) {
            return PROCESSING_STATUS;
        }

        if (NEEDS_REVIEW_STATUS.equals(normalizedStoredStatus)) {
            return NEEDS_REVIEW_STATUS;
        }

        if ("reviewed".equals(normalizedStoredStatus) || "ready".equals(normalizedStoredStatus) || "approved".equals(normalizedStoredStatus)) {
            return normalizedStoredStatus;
        }

        if (hasMissingFileState(uploadedFile) || !fileExists(uploadedFile)) {
            return NEEDS_REVIEW_STATUS;
        }

        return UPLOADED_STATUS;
    }

    private String initialStoredStatus(DocumentType documentType) {
        return documentType == DocumentType.SPREADSHEET ? PROCESSING_STATUS : UPLOADED_STATUS;
    }

    private boolean hasMissingFileState(UploadedFile uploadedFile) {
        return uploadedFile.getUploadedAt() == null
                || uploadedFile.getFileName() == null
                || uploadedFile.getFileName().isBlank()
                || uploadedFile.getFilePath() == null
                || uploadedFile.getFilePath().isBlank()
                || uploadedFile.getContentType() == null
                || uploadedFile.getContentType().isBlank();
    }

    private boolean fileExists(UploadedFile uploadedFile) {
        return storageService.isManagedLocation(uploadedFile.getFilePath());
    }

    private Map<Integer, ReceiptExtraction> extractionsByFileId(Collection<UploadedFile> uploadedFiles) {
        List<Integer> fileIds = uploadedFiles.stream()
                .map(UploadedFile::getId)
                .filter(id -> id != null)
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

    private Map<String, Object> metadataMap(List<String> metadataValues, ReceiptExtraction extraction) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        if (metadataValues == null) {
            mergeExtractionMetadata(metadata, extraction);
            return metadata;
        }

        for (String metadataValue : metadataValues) {
            int separator = metadataValue.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = metadataValue.substring(0, separator);
            String value = metadataValue.substring(separator + 1);
            metadata.put(key, typedMetadataValue(key, value));
        }

        mergeExtractionMetadata(metadata, extraction);
        return metadata;
    }

    private void mergeAiFileMetadata(Map<String, Object> metadata, UploadedFile uploadedFile) {
        if (uploadedFile.getAiSummary() != null && !uploadedFile.getAiSummary().isBlank()) {
            metadata.put("aiSummary", uploadedFile.getAiSummary());
        }
        if (uploadedFile.getAiExtractedData() != null && !uploadedFile.getAiExtractedData().isBlank()) {
            metadata.put("aiExtractedData", uploadedFile.getAiExtractedData());
        }
        if (uploadedFile.getAiProcessedAt() != null) {
            metadata.put("aiProcessedAt", uploadedFile.getAiProcessedAt().toString());
        }
    }
    private void mergeExtractionMetadata(Map<String, Object> metadata, ReceiptExtraction extraction) {
        if (extraction == null) {
            return;
        }

        putIfAbsent(metadata, "merchantName", extraction.getMerchant());
        putIfAbsent(metadata, "date", extraction.getReceiptDate());
        putIfAbsent(metadata, "amount", extraction.getTotal());
        putIfAbsent(metadata, "category", extraction.getSuggestedCategory());
        putIfAbsent(metadata, "confidence", extraction.getConfidence());
        putIfAbsent(metadata, "taxRelevance", extractionTaxRelevance(extraction));
        putIfAbsent(metadata, "subtotal", extraction.getSubtotal());
        putIfAbsent(metadata, "tax", extraction.getTax());
        putIfAbsent(metadata, "currency", extraction.getCurrency());
    }

    private void putIfAbsent(Map<String, Object> metadata, String key, Object value) {
        if (!metadata.containsKey(key) && value != null) {
            metadata.put(key, value);
        }
    }

    private ReceiptExtractionDto extractionDto(ReceiptExtraction extraction) {
        if (extraction == null) {
            return null;
        }

        return new ReceiptExtractionDto(
                extraction.getMerchant(),
                extraction.getReceiptDate(),
                extraction.getSubtotal(),
                extraction.getTax(),
                extraction.getTotal(),
                extraction.getCurrency(),
                extraction.getSuggestedCategory(),
                extractionTaxRelevance(extraction),
                extraction.getConfidence(),
                extraction.getItems() == null
                        ? List.of()
                        : extraction.getItems().stream()
                                .map(item -> new ReceiptItemDto(item.getName(), item.getAmount()))
                                .toList());
    }

    private List<ReceiptExtractionItem> extractionItems(
            List<ReceiptProcessingResponse.ExtractedItem> items,
            ReceiptExtraction extraction) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .map(item -> new ReceiptExtractionItem(null, item.getName(), item.getAmount(), extraction))
                .toList();
    }

    private String normalizeReceiptDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String extractionTaxRelevance(ReceiptExtraction extraction) {
        if (extraction == null || extraction.getPossibleTaxRelevant() == null) {
            return null;
        }
        return extraction.getPossibleTaxRelevant()
                ? "Potential business expense."
                : "No obvious tax relevance detected.";
    }

    private Object typedMetadataValue(String key, String value) {
        if ("amount".equals(key)) {
            return parseBigDecimal(value);
        }

        if ("confidence".equals(key)) {
            return parseDouble(value);
        }

        return value;
    }

    private String normalizeStatus(String value) {
        String normalized = normalize(value).replace('_', ' ');
        if (normalized.isBlank()) {
            return "";
        }
        if ("needs review".equals(normalized) || "failed".equals(normalized) || "error".equals(normalized)) {
            return NEEDS_REVIEW_STATUS;
        }
        if ("processing".equals(normalized)) {
            return PROCESSING_STATUS;
        }
        if ("extracted".equals(normalized)) {
            return EXTRACTED_STATUS;
        }
        if ("uploaded".equals(normalized)) {
            return UPLOADED_STATUS;
        }
        if ("reviewed".equals(normalized) || "ready".equals(normalized) || "approved".equals(normalized)) {
            return normalized;
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Object parseBigDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    private Object parseDouble(String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return value;
        }
    }
}









package com.Accounting.app.files;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.Accounting.app.auth.Config;
import com.Accounting.app.files.dto.FileUploadResponse;
import com.Accounting.app.files.dto.UploadedFileContent;
import com.Accounting.app.files.dto.UploadedFileDto;

import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
public class FileUploadController {
    private final Config config;
    private final FileUploadService fileUploadService;

    @PostMapping("/api/dashboard/file/upload")
    public ResponseEntity<UploadedFileDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer transactionId,
            @RequestParam(required = false) String merchantName,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) String taxRelevance,
            @RequestParam(required = false) String taxYear,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(fileUploadService.uploadFile(
                file,
                config.getEmail(),
                DocumentType.RECEIPT,
                transactionId,
                metadata(merchantName, date, amount, category, confidence, taxRelevance, taxYear, source, notes)));
    }

    @PostMapping("/api/dashboard/files/upload")
    public ResponseEntity<FileUploadResponse> uploadFiles(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files[]", required = false) MultipartFile[] bracketFiles,
            @RequestParam(defaultValue = "RECEIPT") DocumentType documentType,
            @RequestParam(required = false) Integer transactionId,
            @RequestParam(required = false) String merchantName,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false) String taxRelevance,
            @RequestParam(required = false) String taxYear,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(fileUploadService.uploadFiles(
                mergeFiles(files, file, bracketFiles),
                config.getEmail(),
                documentType,
                transactionId,
                metadata(merchantName, date, amount, category, confidence, taxRelevance, taxYear, source, notes)));
    }

    @GetMapping("/api/dashboard/files")
    public List<UploadedFileDto> getUploadedFiles() {
        return fileUploadService.loadUploadedFiles(config.getEmail());
    }

    @PatchMapping("/api/dashboard/files/{fileId}/metadata")
    public ResponseEntity<UploadedFileDto> updateUploadedFileMetadata(
            @PathVariable Integer fileId,
            @RequestBody Map<String, String> updates) {
        return ResponseEntity.ok(fileUploadService.updateUploadedFileMetadata(fileId, config.getEmail(), updates));
    }

    @GetMapping("/api/dashboard/files/{fileId}/content")
    public ResponseEntity<Void> getUploadedFileContent(@PathVariable Integer fileId) {
        UploadedFileContent fileContent = fileUploadService.loadUploadedFileContent(fileId, config.getEmail());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(fileContent.getRedirectUri())
                .build();
    }

    private Map<String, String> metadata(
            String merchantName,
            String date,
            String amount,
            String category,
            String confidence,
            String taxRelevance,
            String taxYear,
            String source,
            String notes) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "merchantName", merchantName);
        putIfPresent(metadata, "date", date);
        putIfPresent(metadata, "amount", amount);
        putIfPresent(metadata, "category", category);
        putIfPresent(metadata, "confidence", confidence);
        putIfPresent(metadata, "taxRelevance", taxRelevance);
        putIfPresent(metadata, "taxYear", taxYear);
        putIfPresent(metadata, "source", source);
        putIfPresent(metadata, "notes", notes);
        return metadata;
    }

    private void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private MultipartFile[] mergeFiles(
            MultipartFile[] files,
            MultipartFile file,
            MultipartFile[] bracketFiles) {
        List<MultipartFile> mergedFiles = new ArrayList<>();

        if (files != null) {
            mergedFiles.addAll(Arrays.asList(files));
        }

        if (file != null) {
            mergedFiles.add(file);
        }

        if (bracketFiles != null) {
            mergedFiles.addAll(Arrays.asList(bracketFiles));
        }

        return mergedFiles.toArray(MultipartFile[]::new);
    }
}

package com.Accounting.app.files.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.Accounting.app.files.DocumentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadedFileDto {
    private Integer id;
    private String fileName;
    private String storedFileName;
    private String fileUrl;
    private String previewUrl;
    private String contentType;
    private String fileType;
    private Long fileSize;
    private Long sizeBytes;
    private LocalDateTime uploadedAt;
    private String status;
    private DocumentType documentType;
    private Integer relatedTransactionId;
    private Map<String, Object> metadata;
    private ReceiptExtractionDto extracted;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptExtractionDto {
        private String merchantName;
        private String date;
        private BigDecimal subtotal;
        private BigDecimal tax;
        private BigDecimal amount;
        private String currency;
        private String category;
        private String taxRelevance;
        private Double confidence;
        private List<ReceiptItemDto> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptItemDto {
        private String name;
        private BigDecimal amount;
    }
}

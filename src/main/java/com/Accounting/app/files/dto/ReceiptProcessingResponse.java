package com.Accounting.app.files.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceiptProcessingResponse {

    private Integer documentId;
    private String status;
    private ExtractedReceipt extracted;
    private RagResult rag;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedReceipt {
        @JsonAlias({ "merchantName" })
        private String merchant;

        @JsonAlias({ "date", "receipt_date" })
        private String receiptDate;

        private BigDecimal subtotal;
        private BigDecimal tax;

        @JsonAlias({ "amount" })
        private BigDecimal total;

        private String currency;

        @JsonAlias({ "category" })
        private String suggestedCategory;

        private List<ExtractedItem> items;

        @JsonAlias({ "taxRelevant" })
        private Boolean possibleTaxRelevant;

        private String taxRelevance;
        private Double confidence;

        public Boolean resolvePossibleTaxRelevant() {
            if (possibleTaxRelevant != null) {
                return possibleTaxRelevant;
            }

            if (taxRelevance == null || taxRelevance.isBlank()) {
                return null;
            }

            String normalized = taxRelevance.trim().toLowerCase();
            if (normalized.contains("no ") || normalized.contains("not ")) {
                return Boolean.FALSE;
            }

            return Boolean.TRUE;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedItem {
        private String name;
        private BigDecimal amount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RagResult {
        private Boolean indexed;
        private String vectorId;
    }

}

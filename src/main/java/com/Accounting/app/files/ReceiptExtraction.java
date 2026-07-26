package com.Accounting.app.files;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class ReceiptExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String merchant;
    private String receiptDate;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String currency;
    private String suggestedCategory;
    private Boolean possibleTaxRelevant;
    private Double confidence;

    @OneToOne
    private UploadedFile uploadedFile;

    @OneToMany(mappedBy = "receiptExtraction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptExtractionItem> items = new ArrayList<>();

    public ReceiptExtraction(
            String merchant,
            String receiptDate,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal total,
            String currency,
            String suggestedCategory,
            Boolean possibleTaxRelevant,
            Double confidence,
            UploadedFile uploadedFile) {
        this.merchant = merchant;
        this.receiptDate = receiptDate;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = total;
        this.currency = currency;
        this.suggestedCategory = suggestedCategory;
        this.possibleTaxRelevant = possibleTaxRelevant;
        this.confidence = confidence;
        this.uploadedFile = uploadedFile;
    }
}

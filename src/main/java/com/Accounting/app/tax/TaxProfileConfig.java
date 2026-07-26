package com.Accounting.app.tax;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tax_profile_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileConfig {
    @Id
    @Column(name = "email", nullable = false)
    private String email;

    private String legalName;
    private String filingStatus;
    private String entityType;
    private String taxResidence;
    private Integer taxYear;
    private Integer taxYearStartMonth;
    private String baseCurrency;

    @Column(name = "raw_config_json", columnDefinition = "TEXT")
    private String rawConfigJson;

    @ElementCollection
    @CollectionTable(name = "tax_profile_jurisdictions", joinColumns = @JoinColumn(name = "config_email", referencedColumnName = "email"))
    private List<TaxProfileJurisdictionConfig> jurisdictions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tax_profile_income_sources", joinColumns = @JoinColumn(name = "config_email", referencedColumnName = "email"))
    private List<TaxProfileIncomeSourceConfig> incomeSources = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tax_profile_deduction_categories", joinColumns = @JoinColumn(name = "config_email", referencedColumnName = "email"))
    private List<TaxProfileDeductionCategoryConfig> deductionCategories = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tax_profile_estimated_payments", joinColumns = @JoinColumn(name = "config_email", referencedColumnName = "email"))
    private List<TaxProfileEstimatedPaymentConfig> estimatedPayments = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tax_profile_deadlines", joinColumns = @JoinColumn(name = "config_email", referencedColumnName = "email"))
    private List<TaxProfileDeadlineConfig> deadlines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tax_profile_data_sources", joinColumns = @JoinColumn(name = "config_email", referencedColumnName = "email"))
    private List<TaxProfileDataSourceConfig> dataSources = new ArrayList<>();
}

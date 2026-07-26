package com.Accounting.app.tax.dto;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfilePageResponse {
    private FilingProfile filingProfile;
    private List<Jurisdiction> jurisdictions;
    private List<IncomeSource> incomeSources;
    private List<DeductionCategory> deductionCategories;
    private List<EstimatedPayment> estimatedPayments;
    private List<Deadline> deadlines;
    private List<DataSource> dataSources;
}

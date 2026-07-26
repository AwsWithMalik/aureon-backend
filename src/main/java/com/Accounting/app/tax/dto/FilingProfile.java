package com.Accounting.app.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilingProfile {
    private String legalName;
    private String filingStatus;
    private String entityType;
    private String taxResidence;
    private Integer taxYear;
    private Integer taxYearStartMonth;
    private String baseCurrency;
}

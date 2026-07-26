package com.Accounting.app.tax;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileJurisdictionConfig {
    private String jurisdictionId;
    private String name;
    private String type;
    private String registrationNumber;
    private String status;
}

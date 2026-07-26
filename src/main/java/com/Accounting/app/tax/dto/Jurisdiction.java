package com.Accounting.app.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Jurisdiction {
    private String id;
    private String name;
    private String type;
    private String registrationNumber;
    private String status;
}

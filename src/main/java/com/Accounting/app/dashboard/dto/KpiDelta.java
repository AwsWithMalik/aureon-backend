package com.Accounting.app.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KpiDelta {
    private String id;
    private String label;
    private String value;
    private String direction;
    private String tone;
}

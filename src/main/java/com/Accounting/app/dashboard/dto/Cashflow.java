package com.Accounting.app.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cashflow {
    private Inflow inflow;
    private Outflow outflow;
}

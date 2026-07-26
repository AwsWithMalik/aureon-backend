package com.Accounting.app.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedReport {
    private String id;
    private String name;
    private String owner;
    private String cadence;
    private String updatedAt;
}

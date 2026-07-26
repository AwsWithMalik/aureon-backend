package com.Accounting.app.tax.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSource {
    private String id;
    private String label;
    private String sourceType;
    private LocalDateTime lastSyncedAt;
    private String status;
}

package com.Accounting.app.tax;

import java.time.LocalDateTime;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileDataSourceConfig {
    private String sourceId;
    private String label;
    private String sourceType;
    private LocalDateTime lastSyncedAt;
    private String status;
}

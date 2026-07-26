package com.Accounting.app.accounts.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationQueue {
    private String id;
    private String accountName;
    private Integer openCount;
    private LocalDateTime lastSyncAt;
    private String status;
    
}

package com.Accounting.app.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentTransactions {
    private String transactionId;
    private String merchant;
    private String category;
    private BigDecimal amount;
    private String status;
    private LocalDateTime occurredAt;
}

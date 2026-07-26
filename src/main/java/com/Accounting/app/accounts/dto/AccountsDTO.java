package com.Accounting.app.accounts.dto;


import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountsDTO {
    private String accountId;
    private String accountName;
    private String institutionName;
    private String institutionId;
    private String institutionLogo;
    private String institutionPrimaryColor;
    private String institutionUrl;
    private String Accounttype;
    private String subtype;
    private String maskedNumber;
    private Balance balance;
    private Balance availableBalance;
    private String lastSyncAt;
    private String status;
    private BigDecimal changePercent;

}

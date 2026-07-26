package com.Accounting.app.settings.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySettings {
    private Boolean mfaEnabled;
    private LocalDateTime lastPasswordChangeAt;
    private Integer activeSessions;
    private String recoveryEmail;
    private String recoveryPhone;
    private Boolean backupCodesEnabled;
    private Integer backupCodesRemaining;
}

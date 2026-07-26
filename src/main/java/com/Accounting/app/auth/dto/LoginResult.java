package com.Accounting.app.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResult {
    private String status;
    private String email;
    private String mfaToken;
}

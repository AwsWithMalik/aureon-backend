package com.Accounting.app.auth;

public record MfaSetupResponse(
        String secret,
        String qrUri) {
}

package com.Accounting.app.auth;

import org.springframework.http.ResponseCookie;

import org.springframework.stereotype.Component;

@Component
public class CookieUtil {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String MFA_TOKEN_COOKIE = "mfa_token";

    private final AuthProperties properties;

    public CookieUtil(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie createAccessTokenCookie(String token) {
        return baseCookie(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .path("/")
                .maxAge(properties.getAccessTokenTtl())
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String token) {
        return baseCookie(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .path("/auth")
                .maxAge(properties.getRefreshTokenTtl())
                .build();
    }

    public ResponseCookie createMfaTokenCookie(String token) {
        return baseCookie(MFA_TOKEN_COOKIE, token)
                .httpOnly(true)
                .path("/auth/mfa")
                .maxAge(5 * 60)
                .build();
    }

    public ResponseCookie clearAccessTokenCookie() {
        return expiredCookie(ACCESS_TOKEN_COOKIE, "/");
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return expiredCookie(REFRESH_TOKEN_COOKIE, "/auth");
    }

    public ResponseCookie clearMfaTokenCookie() {
        return expiredCookie(MFA_TOKEN_COOKIE, "/auth/mfa");
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return baseCookie(name, "")
                .httpOnly(true)
                .path(path)
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite());
        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            builder.domain(properties.getCookieDomain().trim());
        }
        return builder;
    }
}

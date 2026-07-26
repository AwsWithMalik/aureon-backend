package com.Accounting.app.auth;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.audit.AuditLogService;
import com.Accounting.app.auth.AuthSessionService.SessionTokens;
import com.Accounting.app.auth.dto.AuthRegister;
import com.Accounting.app.auth.dto.LoginRequest;
import com.Accounting.app.auth.dto.LoginResponse;
import com.Accounting.app.auth.dto.LoginResult;
import com.Accounting.app.auth.dto.RegisterRequest;
import com.Accounting.app.auth.dto.UserDto;
import com.Accounting.app.exceptions.InvalidInputException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AuthController {
    private final AuthService authService;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final CookieUtil cookieUtil;
    private final AuditLogService auditLogService;

    public AuthController(
            AuthService authService,
            MfaService mfaService,
            JwtService jwtService,
            AuthSessionService authSessionService,
            CookieUtil cookieUtil,
            AuditLogService auditLogService) {
        this.authService = authService;
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.authSessionService = authSessionService;
        this.cookieUtil = cookieUtil;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthRegister> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthRegister response = authService.register(request);
        SessionTokens session = authSessionService.createSession(response.getUser().getEmail());
        auditLogService.recordAuthenticationEvent(
                request.getEmail(),
                "User registered",
                "New account registered and a revocable browser session was created.",
                "Success",
                httpRequest,
                200);
        return withSessionCookies(ResponseEntity.ok(), session).body(response);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            LoginResult result = authService.login(request);
            if ("MFA_REQUIRED".equals(result.getStatus())) {
                ResponseCookie mfaCookie = cookieUtil.createMfaTokenCookie(result.getMfaToken());
                auditLogService.recordAuthenticationEvent(
                        request.getEmail(), "MFA required",
                        "Password verified. MFA verification required.",
                        "Success", httpRequest, 200);
                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, mfaCookie.toString())
                        .body(new LoginResponse("MFA_REQUIRED", null));
            }

            SessionTokens session = authSessionService.createSession(result.getEmail());
            UserDto user = authService.currentUser(result.getEmail());
            auditLogService.recordAuthenticationEvent(
                    request.getEmail(), "User signed in",
                    "Revocable browser session created after successful login.",
                    "Success", httpRequest, 200);
            return withSessionCookies(ResponseEntity.ok(), session)
                    .body(new LoginResponse("AUTHENTICATED", user));
        } catch (RuntimeException exception) {
            auditLogService.recordAuthenticationEvent(
                    request.getEmail(), "Sign in failed", "Login attempt failed.",
                    "Failed", httpRequest, 401);
            throw exception;
        }
    }

    @PostMapping("/auth/mfa/verify")
    public ResponseEntity<LoginResponse> verifyMfaLogin(
            @RequestBody Map<String, Object> requestBody,
            @CookieValue(name = CookieUtil.MFA_TOKEN_COOKIE, required = false) String mfaToken,
            HttpServletRequest httpRequest) {
        String email = null;
        try {
            String code = valueFrom(requestBody, "code", "mfaCode", "totpCode", "verificationCode");
            if (code == null || code.isBlank()) {
                throw new InvalidInputException("MFA code is required.");
            }
            if (mfaToken != null && !mfaToken.isBlank()) {
                email = jwtService.extractEmailFromMfaToken(mfaToken);
            }

            LoginResult result = mfaService.verifyMfaLogin(mfaToken, code);
            SessionTokens session = authSessionService.createSession(result.getEmail());
            UserDto user = authService.currentUser(result.getEmail());
            auditLogService.recordAuthenticationEvent(
                    email, "MFA verified",
                    "Revocable browser session created after MFA verification.",
                    "Success", httpRequest, 200);
            return withSessionCookies(ResponseEntity.ok(), session)
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.clearMfaTokenCookie().toString())
                    .body(new LoginResponse("AUTHENTICATED", user));
        } catch (RuntimeException exception) {
            auditLogService.recordAuthenticationEvent(
                    email, "MFA verification failed", "MFA login verification failed.",
                    "Failed", httpRequest, 401);
            throw exception;
        }
    }

    @RequestMapping(path = "/auth/me", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<Map<String, UserDto>> currentUser(Authentication authentication) {
        return ResponseEntity.ok(Map.of("user", authService.currentUser(authentication.getName())));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        SessionTokens session = authSessionService.rotate(refreshToken);
        UserDto user = authService.currentUser(session.user().getEmail());
        return withSessionCookies(ResponseEntity.ok(), session)
                .body(new LoginResponse("AUTHENTICATED", user));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @CookieValue(name = CookieUtil.ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            HttpServletRequest request) {
        UUID sessionId = null;
        if (accessToken != null && jwtService.isTokenValid(accessToken)) {
            sessionId = jwtService.extractSessionId(accessToken);
        }
        authSessionService.revoke(refreshToken, sessionId);
        auditLogService.recordAuthenticationEvent(
                null, "User signed out",
                "Browser session revoked and authentication cookies expired.",
                "Success", request, 204);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearMfaTokenCookie().toString())
                .build();
    }

    private ResponseEntity.BodyBuilder withSessionCookies(
            ResponseEntity.BodyBuilder response,
            SessionTokens session) {
        String accessToken = jwtService.generateAccessToken(
                session.user().getEmail(),
                session.sessionId());
        return response
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(accessToken).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(session.refreshToken()).toString());
    }

    private String valueFrom(Map<String, Object> requestBody, String... keys) {
        if (requestBody == null) {
            return null;
        }
        for (String key : keys) {
            Object value = requestBody.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}

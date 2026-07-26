package com.Accounting.app.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.Accounting.app.exceptions.UserNotFoundException;

@Service
public class AuthSessionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthSessionRepo authSessionRepo;
    private final UserRepo userRepo;
    private final AuthProperties properties;

    public AuthSessionService(
            AuthSessionRepo authSessionRepo,
            UserRepo userRepo,
            AuthProperties properties) {
        this.authSessionRepo = authSessionRepo;
        this.userRepo = userRepo;
        this.properties = properties;
    }

    @Transactional
    public SessionTokens createSession(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Instant now = Instant.now();
        String refreshToken = randomToken();

        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setRefreshTokenHash(hash(refreshToken));
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(properties.getRefreshTokenTtl()));
        authSessionRepo.save(session);

        return new SessionTokens(session.getId(), refreshToken, session.getExpiresAt(), user);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public SessionTokens rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw unauthorized();
        }

        String incomingHash = hash(refreshToken);
        AuthSession session = authSessionRepo.findByRefreshTokenHashForUpdate(incomingHash)
                .orElseThrow(this::unauthorized);
        Instant now = Instant.now();
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            session.setRevokedAt(now);
            throw unauthorized();
        }
        if (incomingHash.equals(session.getPreviousRefreshTokenHash())) {
            session.setRevokedAt(now);
            throw unauthorized();
        }

        String rotatedRefreshToken = randomToken();
        session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
        session.setRefreshTokenHash(hash(rotatedRefreshToken));
        session.setLastUsedAt(now);
        authSessionRepo.save(session);
        return new SessionTokens(
                session.getId(),
                rotatedRefreshToken,
                session.getExpiresAt(),
                session.getUser());
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, String email) {
        return sessionId != null
                && email != null
                && authSessionRepo.existsByIdAndUser_EmailAndRevokedAtIsNullAndExpiresAtAfter(
                        sessionId,
                        email,
                        Instant.now());
    }

    @Transactional
    public void revoke(String refreshToken, UUID sessionId) {
        Instant now = Instant.now();
        if (refreshToken != null && !refreshToken.isBlank()) {
            java.util.Optional<AuthSession> session =
                    authSessionRepo.findByRefreshTokenHashForUpdate(hash(refreshToken));
            if (session.isPresent()) {
                session.get().setRevokedAt(now);
                return;
            }
        }

        if (sessionId != null) {
            authSessionRepo.findById(sessionId)
                    .ifPresent(session -> session.setRevokedAt(now));
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication session is invalid or expired");
    }

    public record SessionTokens(
            UUID sessionId,
            String refreshToken,
            Instant refreshExpiresAt,
            User user) {
    }
}

package com.Accounting.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private final JwtService jwtService = service();

    @Test
    void accessTokenCarriesAndValidatesSessionId() {
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateAccessToken("person@example.com", sessionId);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("person@example.com", jwtService.extractEmail(token));
        assertEquals(sessionId, jwtService.extractSessionId(token));
    }

    @Test
    void mfaTokenCannotBeUsedAsAccessToken() {
        String token = jwtService.generateMfaToken("person@example.com");

        assertTrue(jwtService.isMfaTokenValid(token));
        assertFalse(jwtService.isTokenValid(token));
    }

    private JwtService service() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("unit-test-secret-that-is-definitely-at-least-thirty-two-characters");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        return new JwtService(properties);
    }
}

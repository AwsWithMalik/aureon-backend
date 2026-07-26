package com.Accounting.app.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final AuthProperties properties;

    public JwtService(AuthProperties properties) {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured with at least 32 characters");
        }
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateAccessToken(String email, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(email)
                .claim("type", "ACCESS")
                .claim("sid", sessionId.toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractEmailFromMfaToken(String token) {
        Claims claims = extractClaims(token);
        String tokenType = claims.get("type", String.class);
        if (!"MFA".equals(tokenType)) {
            throw new JwtException("Invalid MFA token");
        }
        return claims.getSubject();
    }

    public Boolean isTokenValid(String token, String email) {
        return isAccessTokenValid(token, email);
    }

    public String generateMfaToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("type", "MFA")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return "ACCESS".equals(claims.get("type", String.class))
                    && claims.get("sid", String.class) != null
                    && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaims(token).getExpiration();

        return expiration != null && expiration.before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractTokenType(String token) {
        return extractClaims(token).get("type", String.class);
    }

    public boolean isAccessTokenValid(String token, String email) {
        try {
            Claims claims = extractClaims(token);

            String extractedEmail = claims.getSubject();
            String tokenType = claims.get("type", String.class);
            String sessionId = claims.get("sid", String.class);
            Date expiration = claims.getExpiration();

            return extractedEmail.equals(email)
                    && "ACCESS".equals(tokenType)
                    && sessionId != null
                    && expiration != null
                    && expiration.after(new Date());

        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isMfaTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);

            String tokenType = claims.get("type", String.class);
            Date expiration = claims.getExpiration();

            return "MFA".equals(tokenType)
                    && expiration != null
                    && expiration.after(new Date());

        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public UUID extractSessionId(String token) {
        return UUID.fromString(extractClaims(token).get("sid", String.class));
    }
}

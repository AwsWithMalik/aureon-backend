package com.Accounting.app.config;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class AllowedOriginPolicy {
    private final Set<String> allowedOrigins;

    public AllowedOriginPolicy(@Value("${app.security.allowed-origins}") String configuredOrigins) {
        this.allowedOrigins = new LinkedHashSet<>();
        Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalizeOrigin)
                .forEach(allowedOrigins::add);
    }

    public List<String> allowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public boolean isAllowed(HttpServletRequest request, String candidate) {
        String normalized = normalizeOrigin(candidate);
        return allowedOrigins.contains(normalized) || requestOrigin(request).equals(normalized);
    }

    private String requestOrigin(HttpServletRequest request) {
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && !(scheme.equals("https") && port == 443) && !(scheme.equals("http") && port == 80)) {
                host += ":" + port;
            }
        }
        return normalizeOrigin(scheme + "://" + host);
    }

    private String firstHeaderValue(String value) {
        return value == null ? null : value.split(",", 2)[0].trim();
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = port < 0 || ("https".equals(scheme) && port == 443)
                    || ("http".equals(scheme) && port == 80);
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}

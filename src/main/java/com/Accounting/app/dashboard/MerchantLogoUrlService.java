package com.Accounting.app.dashboard;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class MerchantLogoUrlService {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "plaid-merchant-logos.plaid.com");

    public String toClientLogoUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        String normalized = rawUrl.trim();
        if (normalized.startsWith("data:") || normalized.startsWith("/")) {
            return normalized;
        }

        String lower = normalized.toLowerCase(Locale.US);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return null;
        }

        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.US))) {
                return null;
            }

            return "/api/dashboard/logos/merchant?source="
                    + URLEncoder.encode(normalized, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

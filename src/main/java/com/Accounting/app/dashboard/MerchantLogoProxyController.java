package com.Accounting.app.dashboard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantLogoProxyController {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "plaid-merchant-logos.plaid.com");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @GetMapping("/api/dashboard/logos/merchant")
    public ResponseEntity<byte[]> merchantLogo(@RequestParam String source) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(source);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                || host == null
                || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.US))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .header("Accept", "image/*")
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        MediaType mediaType = mediaType(response.headers().firstValue("Content-Type").orElse(null));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(response.body());
    }

    private MediaType mediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.IMAGE_PNG;
        }

        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ex) {
            return MediaType.IMAGE_PNG;
        }
    }
}

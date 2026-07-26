package com.Accounting.app.files.storage;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.Accounting.app.files.DocumentType;

@Component
public class S3ObjectKeyFactory {
    private static final int MAX_STORED_FILENAME_LENGTH = 120;

    private final Clock clock;

    public S3ObjectKeyFactory() {
        this(Clock.systemUTC());
    }

    S3ObjectKeyFactory(Clock clock) {
        this.clock = clock;
    }

    public String create(Integer userId, DocumentType documentType, String originalFilename) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted user is required for document storage");
        }

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String type = documentType == null
                ? "other"
                : documentType.name().toLowerCase(Locale.ROOT).replace('_', '-');
        String storedFileName = UUID.randomUUID() + "-" + safeFilename(originalFilename);

        return "users/%d/documents/%s/%d/%02d/%s".formatted(
                userId,
                type,
                today.getYear(),
                today.getMonthValue(),
                storedFileName);
    }

    static String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }

        String safe = originalFilename
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_");
        safe = safe.replaceAll("^\\.+", "");
        if (safe.isBlank()) {
            return "upload";
        }
        if (safe.length() <= MAX_STORED_FILENAME_LENGTH) {
            return safe;
        }

        int extensionStart = safe.lastIndexOf('.');
        String extension = extensionStart > 0 ? safe.substring(extensionStart) : "";
        int baseLength = MAX_STORED_FILENAME_LENGTH - extension.length();
        if (baseLength < 1) {
            return safe.substring(0, MAX_STORED_FILENAME_LENGTH);
        }
        return safe.substring(0, baseLength) + extension;
    }
}

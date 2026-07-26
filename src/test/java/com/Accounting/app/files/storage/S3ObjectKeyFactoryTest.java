package com.Accounting.app.files.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.Accounting.app.files.DocumentType;

class S3ObjectKeyFactoryTest {

    private final S3ObjectKeyFactory keyFactory = new S3ObjectKeyFactory(
            Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsUserAndDocumentTypeScopedKey() {
        String key = keyFactory.create(42, DocumentType.TAX_DOCUMENT, "T4 2025.pdf");

        assertTrue(key.matches(
                "users/42/documents/tax-document/2026/07/[0-9a-f-]{36}-T4_2025\\.pdf"));
    }

    @Test
    void removesPathSegmentsFromFilename() {
        String key = keyFactory.create(7, DocumentType.RECEIPT, "../../private/receipt.png");

        assertTrue(key.startsWith("users/7/documents/receipt/2026/07/"));
        assertFalse(key.contains("../"));
        assertFalse(key.contains("\\"));
    }

    @Test
    void limitsStoredFilenameLengthAndPreservesExtension() {
        String filename = "a".repeat(200) + ".pdf";

        String safeFilename = S3ObjectKeyFactory.safeFilename(filename);

        assertEquals(120, safeFilename.length());
        assertTrue(safeFilename.endsWith(".pdf"));
    }
}

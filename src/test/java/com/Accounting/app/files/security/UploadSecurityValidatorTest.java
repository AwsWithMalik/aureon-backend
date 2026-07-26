package com.Accounting.app.files.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.files.security.UploadSecurityValidator.ValidatedUpload;

class UploadSecurityValidatorTest {
    private final UploadSecurityValidator validator = new UploadSecurityValidator(
            new MalwareScanner(false, "localhost", 3310, Duration.ofSeconds(1), Duration.ofSeconds(1)));

    @Test
    void acceptsPdfOnlyWhenItsSignatureMatches() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7\ncontent".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        ValidatedUpload validated = validator.validateDocument(file);

        assertEquals("receipt.pdf", validated.originalFilename());
        assertEquals("application/pdf", validated.contentType());
    }

    @Test
    void rejectsContentTypeSpoofing() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malware.pdf",
                "application/pdf",
                "MZ executable".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThrows(InvalidInputException.class, () -> validator.validateDocument(file));
    }

    @Test
    void acceptsStructurallyValidXlsxArchive() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<workbook/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ledger.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes.toByteArray());

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                validator.validateDocument(file).contentType());
    }

    @Test
    void rejectsMoreThanTenFilesBeforeStorageBegins() {
        MockMultipartFile[] files = new MockMultipartFile[11];
        for (int index = 0; index < files.length; index++) {
            files[index] = new MockMultipartFile(
                    "files",
                    "file-" + index + ".txt",
                    "text/plain",
                    "safe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        assertThrows(InvalidInputException.class, () -> validator.validateBatch(files));
    }
}

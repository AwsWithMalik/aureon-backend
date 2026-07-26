package com.Accounting.app.files.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.Accounting.app.exceptions.InvalidInputException;

@Component
public class UploadSecurityValidator {
    public static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
    public static final long MAX_BATCH_BYTES = 100L * 1024L * 1024L;
    public static final int MAX_FILES = 10;
    private static final long MAX_XLSX_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L;
    private static final int MAX_XLSX_ENTRIES = 10_000;
    private static final long MAX_PROFILE_PHOTO_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "pdf", "txt", "csv", "xls", "xlsx");

    private final MalwareScanner malwareScanner;

    public UploadSecurityValidator(MalwareScanner malwareScanner) {
        this.malwareScanner = malwareScanner;
    }

    public void validateBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new InvalidInputException("At least one file is required");
        }
        if (files.length > MAX_FILES) {
            throw new InvalidInputException("A maximum of 10 files can be uploaded at once");
        }
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (file == null) {
                throw new InvalidInputException("Upload contains an invalid file");
            }
            totalBytes = Math.addExact(totalBytes, file.getSize());
            if (totalBytes > MAX_BATCH_BYTES) {
                throw new InvalidInputException("The total upload size must not exceed 100 MB");
            }
        }
    }

    public ValidatedUpload validateDocument(MultipartFile file) {
        validatePresentAndSize(file, MAX_FILE_BYTES, "File must be 25 MB or smaller");
        String filename = sanitizedFilename(file.getOriginalFilename());
        String extension = extension(filename);
        if (!DOCUMENT_EXTENSIONS.contains(extension)) {
            throw unsupportedDocument();
        }
        String suppliedContentType = normalizeContentType(file.getContentType());
        String canonicalContentType = canonicalDocumentContentType(extension);
        if (!contentTypeMatches(extension, suppliedContentType)) {
            throw new InvalidInputException("The file extension and content type do not match");
        }
        try {
            validateDocumentSignature(file, extension);
        } catch (IOException exception) {
            throw new InvalidInputException("The uploaded file could not be inspected");
        }
        malwareScanner.scan(file);
        return new ValidatedUpload(filename, canonicalContentType);
    }

    public ValidatedUpload validateProfilePhoto(MultipartFile file) {
        validatePresentAndSize(file, MAX_PROFILE_PHOTO_BYTES, "Profile photo must be 5 MB or smaller");
        String filename = sanitizedFilename(file.getOriginalFilename());
        String extension = extension(filename);
        String suppliedContentType = normalizeContentType(file.getContentType());
        String canonicalContentType;
        try {
            canonicalContentType = switch (extension) {
                case "png" -> {
                    requirePrefix(file, hex("89504e470d0a1a0a"));
                    yield "image/png";
                }
                case "jpg", "jpeg" -> {
                    requirePrefix(file, hex("ffd8ff"));
                    yield "image/jpeg";
                }
                case "webp" -> {
                    requireWebp(file);
                    yield "image/webp";
                }
                default -> throw new InvalidInputException("Only PNG, JPG, and WebP profile photos are allowed");
            };
        } catch (IOException exception) {
            throw new InvalidInputException("The profile photo could not be inspected");
        }
        if (!suppliedContentType.isBlank()
                && !"application/octet-stream".equals(suppliedContentType)
                && !canonicalContentType.equals(suppliedContentType)
                && !("image/jpeg".equals(canonicalContentType) && "image/jpg".equals(suppliedContentType))) {
            throw new InvalidInputException("The profile photo extension and content type do not match");
        }
        malwareScanner.scan(file);
        return new ValidatedUpload(filename, canonicalContentType);
    }

    private void validatePresentAndSize(MultipartFile file, long maxBytes, String sizeMessage) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("File is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidInputException(sizeMessage);
        }
    }

    private void validateDocumentSignature(MultipartFile file, String extension) throws IOException {
        switch (extension) {
            case "png" -> requirePrefix(file, hex("89504e470d0a1a0a"));
            case "jpg", "jpeg" -> requirePrefix(file, hex("ffd8ff"));
            case "pdf" -> requirePrefix(file, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "xls" -> requirePrefix(file, hex("d0cf11e0a1b11ae1"));
            case "xlsx" -> validateXlsx(file);
            case "txt", "csv" -> validateTextFile(file);
            default -> throw unsupportedDocument();
        }
    }

    private void validateTextFile(MultipartFile file) throws IOException {
        byte[] buffer = new byte[8192];
        try (InputStream input = file.getInputStream()) {
            int read = input.read(buffer);
            for (int index = 0; index < read; index++) {
                int value = buffer[index] & 0xff;
                if (value == 0 || value < 0x09 || (value > 0x0d && value < 0x20)) {
                    throw new InvalidInputException("Text and CSV uploads must contain plain text");
                }
            }
        }
    }

    private void validateXlsx(MultipartFile file) throws IOException {
        byte[] header = prefix(file, 8);
        if (header.length < 8 || header[0] != 'P' || header[1] != 'K' || header[2] != 3 || header[3] != 4) {
            throw new InvalidInputException("The XLSX file signature is invalid");
        }
        int flags = (header[6] & 0xff) | ((header[7] & 0xff) << 8);
        if ((flags & 1) != 0) {
            throw new InvalidInputException("Encrypted spreadsheets are not supported");
        }

        boolean hasContentTypes = false;
        boolean hasWorkbook = false;
        long expandedBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_XLSX_ENTRIES) {
                    throw new InvalidInputException("Spreadsheet contains too many entries");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) {
                    throw new InvalidInputException("Spreadsheet contains an unsafe entry path");
                }
                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasWorkbook |= "xl/workbook.xml".equals(name);
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > MAX_XLSX_UNCOMPRESSED_BYTES) {
                        throw new InvalidInputException("Spreadsheet expands beyond the safe processing limit");
                    }
                }
            }
        } catch (ZipException exception) {
            throw new InvalidInputException("Spreadsheet archive is invalid or encrypted");
        }
        if (!hasContentTypes || !hasWorkbook) {
            throw new InvalidInputException("The upload is not a valid XLSX spreadsheet");
        }
    }

    private boolean contentTypeMatches(String extension, String supplied) {
        if (supplied.isBlank() || "application/octet-stream".equals(supplied)) {
            return true;
        }
        return switch (extension) {
            case "png" -> "image/png".equals(supplied);
            case "jpg", "jpeg" -> "image/jpeg".equals(supplied) || "image/jpg".equals(supplied);
            case "pdf" -> "application/pdf".equals(supplied);
            case "txt" -> "text/plain".equals(supplied);
            case "csv" -> Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain")
                    .contains(supplied);
            case "xls" -> "application/vnd.ms-excel".equals(supplied);
            case "xlsx" -> Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip").contains(supplied);
            default -> false;
        };
    }

    private String canonicalDocumentContentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> throw unsupportedDocument();
        };
    }

    private void requirePrefix(MultipartFile file, byte[] expected) throws IOException {
        if (!Arrays.equals(prefix(file, expected.length), expected)) {
            throw new InvalidInputException("The file contents do not match the selected file type");
        }
    }

    private void requireWebp(MultipartFile file) throws IOException {
        byte[] value = prefix(file, 12);
        boolean valid = value.length == 12
                && value[0] == 'R' && value[1] == 'I' && value[2] == 'F' && value[3] == 'F'
                && value[8] == 'W' && value[9] == 'E' && value[10] == 'B' && value[11] == 'P';
        if (!valid) {
            throw new InvalidInputException("The profile photo contents do not match the selected file type");
        }
    }

    private byte[] prefix(MultipartFile file, int length) throws IOException {
        byte[] buffer = new byte[length];
        try (InputStream input = file.getInputStream()) {
            int offset = 0;
            while (offset < length) {
                int read = input.read(buffer, offset, length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            return offset == length ? buffer : Arrays.copyOf(buffer, offset);
        }
    }

    private byte[] hex(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }

    private String sanitizedFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidInputException("A filename with a supported extension is required");
        }
        String filename = Paths.get(originalFilename.replace("\\", "/")).getFileName().toString();
        if (filename.isBlank() || filename.length() > 255) {
            throw new InvalidInputException("Filename is invalid or too long");
        }
        return filename;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private InvalidInputException unsupportedDocument() {
        return new InvalidInputException("Only PNG, JPG, JPEG, PDF, TXT, CSV, XLS, and XLSX files are allowed");
    }

    public record ValidatedUpload(String originalFilename, String contentType) {
    }
}

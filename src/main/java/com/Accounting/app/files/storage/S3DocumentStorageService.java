package com.Accounting.app.files.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.files.DocumentType;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3DocumentStorageService {
    private static final Logger log = LoggerFactory.getLogger(S3DocumentStorageService.class);
    private static final String S3_SCHEME = "s3://";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;
    private final S3ObjectKeyFactory keyFactory;

    public S3DocumentStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3StorageProperties properties,
            S3ObjectKeyFactory keyFactory) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.keyFactory = keyFactory;
    }

    public StoredS3Object store(
            MultipartFile file,
            Integer userId,
            DocumentType documentType,
            String originalFilename) throws IOException {
        return store(file, userId, documentType, originalFilename, file.getContentType());
    }

    public StoredS3Object store(
            MultipartFile file,
            Integer userId,
            DocumentType documentType,
            String originalFilename,
            String validatedContentType) throws IOException {
        String objectKey = keyFactory.create(userId, documentType, originalFilename);
        String contentType = validatedContentType == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : validatedContentType;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(Map.of(
                        "user-id", String.valueOf(userId),
                        "document-type", documentType == null ? "OTHER" : documentType.name()))
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return new StoredS3Object(
                S3_SCHEME + properties.bucket() + "/" + objectKey,
                objectKey,
                objectKey.substring(objectKey.lastIndexOf('/') + 1));
    }

    public StoredS3Object storeProfilePhoto(
            MultipartFile file,
            Integer userId,
            String originalFilename) throws IOException {
        return storeProfilePhoto(file, userId, originalFilename, file.getContentType());
    }

    public StoredS3Object storeProfilePhoto(
            MultipartFile file,
            Integer userId,
            String originalFilename,
            String validatedContentType) throws IOException {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted user is required for profile photo storage");
        }

        String objectKey = "users/%d/profile/%s-%s".formatted(
                userId,
                java.util.UUID.randomUUID(),
                S3ObjectKeyFactory.safeFilename(originalFilename));
        String contentType = validatedContentType == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : validatedContentType;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(Map.of(
                        "user-id", String.valueOf(userId),
                        "document-type", "PROFILE_PHOTO"))
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return new StoredS3Object(
                S3_SCHEME + properties.bucket() + "/" + objectKey,
                objectKey,
                objectKey.substring(objectKey.lastIndexOf('/') + 1));
    }

    public URI createPresignedDownloadUri(String location, String originalFilename) {
        return createPresignedUri(location, originalFilename, "attachment");
    }

    public URI createPresignedInlineUri(String location, String originalFilename) {
        return createPresignedUri(location, originalFilename, "inline");
    }

    private URI createPresignedUri(String location, String originalFilename, String dispositionType) {
        String objectKey = objectKey(location);
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .responseContentDisposition(contentDisposition(dispositionType, originalFilename))
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.presignedUrlDurationSeconds()))
                .getObjectRequest(objectRequest)
                .build();

        return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
    }

    public String storedFileName(String location) {
        String key = objectKey(location);
        return key.substring(key.lastIndexOf('/') + 1);
    }

    public boolean isManagedLocation(String location) {
        return location != null && location.startsWith(S3_SCHEME + properties.bucket() + "/");
    }

    public void deleteIfManagedLocation(String location) {
        if (!isManagedLocation(location)) {
            return;
        }

        try {
            s3Client.deleteObject(request -> request
                    .bucket(properties.bucket())
                    .key(objectKey(location)));
        } catch (RuntimeException ex) {
            log.warn("Failed to delete old S3 object {}: {}", location, ex.getMessage());
        }
    }

    private String objectKey(String location) {
        String prefix = S3_SCHEME + properties.bucket() + "/";
        if (location == null || !location.startsWith(prefix) || location.length() == prefix.length()) {
            throw new InvalidInputException("Invalid S3 document location");
        }
        return location.substring(prefix.length());
    }

    private String contentDisposition(String dispositionType, String originalFilename) {
        String safeAsciiName = S3ObjectKeyFactory.safeFilename(originalFilename).replace("\"", "_");
        String encodedName = URLEncoder.encode(
                originalFilename == null ? "download" : originalFilename,
                StandardCharsets.UTF_8).replace("+", "%20");
        return dispositionType + "; filename=\"" + safeAsciiName + "\"; filename*=UTF-8''" + encodedName;
    }
}

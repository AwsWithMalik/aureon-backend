package com.Accounting.app.files.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.Accounting.app.files.DocumentType;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3DocumentStorageServiceTest {

    @Test
    void uploadsToConfiguredBucketAndReturnsS3Location() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3DocumentStorageService service = new S3DocumentStorageService(
                s3Client,
                mock(S3Presigner.class),
                new S3StorageProperties("aureon-testing-receipts-123456789012", "ca-central-1", 600),
                new S3ObjectKeyFactory(
                        Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC)));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "pdf-content".getBytes());

        StoredS3Object stored = service.store(file, 19, DocumentType.RECEIPT, "receipt.pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("aureon-testing-receipts-123456789012", request.getValue().bucket());
        assertEquals("application/pdf", request.getValue().contentType());
        assertTrue(request.getValue().key().startsWith("users/19/documents/receipt/2026/07/"));
        assertEquals("s3://" + request.getValue().bucket() + "/" + request.getValue().key(), stored.location());
        assertTrue(service.isManagedLocation(stored.location()));
    }

    @Test
    void createsShortLivedPresignedDownloadUrl() {
        S3StorageProperties properties = new S3StorageProperties(
                "aureon-testing-receipts-123456789012",
                "ca-central-1",
                600);
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.CA_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            S3DocumentStorageService service = new S3DocumentStorageService(
                    mock(S3Client.class),
                    presigner,
                    properties,
                    new S3ObjectKeyFactory());

            String url = service.createPresignedDownloadUri(
                    "s3://aureon-testing-receipts-123456789012/users/19/documents/receipt/2026/07/id-receipt.pdf",
                    "July receipt.pdf").toString();

            assertTrue(url.contains("X-Amz-Signature="));
            assertTrue(url.contains("response-content-disposition="));
            assertTrue(url.contains("X-Amz-Expires=600"));
        }
    }
}

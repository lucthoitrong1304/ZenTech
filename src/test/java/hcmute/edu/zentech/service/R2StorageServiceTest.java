package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.UploadPresignResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {
    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    private R2StorageService r2StorageService;

    @BeforeEach
    void setUp() {
        r2StorageService = new R2StorageService(s3Presigner, s3Client);
        ReflectionTestUtils.setField(r2StorageService, "bucketName", "zentech-media");
        ReflectionTestUtils.setField(r2StorageService, "expirationMinutes", 15L);
    }

    @Test
    void generateReviewImagePresignedUrlReturnsPutUrlAndScopedKey() throws Exception {
        UUID userId = UUID.randomUUID();
        when(presignedPutObjectRequest.url()).thenReturn(new URL("https://example.com/upload"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPutObjectRequest);

        UploadPresignResponse response = r2StorageService.generateReviewImagePresignedUrl(
                userId,
                "../My Image.webp",
                "image/webp",
                1024L
        );

        assertThat(response.getPresignedUrl()).isEqualTo("https://example.com/upload");
        assertThat(response.getMethod()).isEqualTo("PUT");
        assertThat(response.getExpiresInMinutes()).isEqualTo(15L);
        assertThat(response.getRequiredHeaders()).containsEntry("Content-Type", "image/webp");
        assertThat(response.getFileKey()).startsWith("uploads/reviews/" + userId + "/");
        assertThat(response.getFileKey()).endsWith("-My-Image.webp");
    }

    @Test
    void generateReviewImagePresignedUrlRejectsUnsupportedContentType() {
        assertThatThrownBy(() -> r2StorageService.generateReviewImagePresignedUrl(
                UUID.randomUUID(),
                "image.gif",
                "image/gif",
                1024L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPEG, PNG, and WEBP");

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void generateReviewImagePresignedUrlRejectsOversizedFile() {
        assertThatThrownBy(() -> r2StorageService.generateReviewImagePresignedUrl(
                UUID.randomUUID(),
                "image.jpg",
                "image/jpeg",
                5 * 1024 * 1024 + 1L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void validateUploadedReviewImageChecksObjectMetadata() {
        UUID userId = UUID.randomUUID();
        String fileKey = "uploads/reviews/" + userId + "/image.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/jpeg")
                .contentLength(1024L)
                .build());

        r2StorageService.validateUploadedReviewImage(fileKey, userId);

        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void validateUploadedReviewImageRejectsMissingObject() {
        UUID userId = UUID.randomUUID();
        String fileKey = "uploads/reviews/" + userId + "/image.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404)
                .build());

        assertThatThrownBy(() -> r2StorageService.validateUploadedReviewImage(fileKey, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }
}

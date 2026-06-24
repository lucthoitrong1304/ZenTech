package hcmute.edu.zentech.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceReturnEvidenceTest {
    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private R2StorageService service;

    @BeforeEach
    void setUp() {
        service = new R2StorageService(s3Presigner, s3Client);
        ReflectionTestUtils.setField(service, "bucketName", "zentech-media");
    }

    @Test
    void promotesExistingTempObjectWithoutDeletingIt() {
        UUID accountId = UUID.randomUUID();
        String tempKey = "temp/returns/" + accountId + "/proof.png";
        String permanentKey = "evidence/returns/" + accountId + "/proof.png";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertEquals(permanentKey, service.promoteReturnEvidence(tempKey, accountId));

        ArgumentCaptor<CopyObjectRequest> copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        assertEquals(permanentKey, copyCaptor.getValue().destinationKey());
        verify(s3Client, never()).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    void acceptsRetryWhenOnlyPermanentObjectExists() {
        UUID accountId = UUID.randomUUID();
        String tempKey = "temp/returns/" + accountId + "/proof.webp";
        String permanentKey = "evidence/returns/" + accountId + "/proof.webp";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(notFound())
                .thenReturn(HeadObjectResponse.builder().build());

        assertEquals(permanentKey, service.promoteReturnEvidence(tempKey, accountId));
        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void rejectsMissingEvidenceAndKeysOwnedByAnotherAccount() {
        UUID accountId = UUID.randomUUID();
        String tempKey = "temp/returns/" + accountId + "/missing.png";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(notFound());

        assertThrows(IllegalArgumentException.class,
                () -> service.promoteReturnEvidence(tempKey, accountId));
        assertThrows(IllegalArgumentException.class,
                () -> service.promoteReturnEvidence(
                        "temp/returns/" + UUID.randomUUID() + "/proof.png",
                        accountId));
    }

    private NoSuchKeyException notFound() {
        return NoSuchKeyException.builder().message("Not found").build();
    }
}

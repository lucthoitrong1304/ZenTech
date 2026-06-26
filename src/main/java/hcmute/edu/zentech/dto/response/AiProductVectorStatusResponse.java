package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.AiProductVectorSyncStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AiProductVectorStatusResponse {
    private UUID productId;
    private UUID variantId;
    private String productName;
    private String variantName;
    private String imageKey;
    private AiProductVectorSyncStatus syncStatus;
    private Instant lastSyncedAt;
    private Instant lastVerifiedAt;
    private Boolean qdrantPresent;
    private String errorMessage;
}

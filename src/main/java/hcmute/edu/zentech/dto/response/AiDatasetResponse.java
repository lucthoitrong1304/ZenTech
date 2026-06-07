package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.AiDatasetStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AiDatasetResponse {
    private UUID id;
    private String name;
    private String description;
    private AiDatasetStatus status;
    private UUID createdBy;
    private List<AiDocumentResponse> documents;
    private int documentCount;
    private Instant createdAt;
    private Instant updatedAt;
}

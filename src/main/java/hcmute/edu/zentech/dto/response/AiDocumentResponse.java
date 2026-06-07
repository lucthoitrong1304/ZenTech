package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.AiDocumentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AiDocumentResponse {
    private UUID id;
    private UUID datasetId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private AiDocumentStatus ingestStatus;
    private int chunkCount;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}

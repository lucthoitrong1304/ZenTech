package hcmute.edu.zentech.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AiKnowledgeIngestRequest {
    private UUID datasetId;
    private UUID documentId;
    private List<UUID> agentIds;
    private String fileName;
    private String contentType;
    private String contentBase64;
}

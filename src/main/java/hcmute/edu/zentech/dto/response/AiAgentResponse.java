package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.AiAgentStatus;
import hcmute.edu.zentech.model.Role;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class AiAgentResponse {
    private UUID id;
    private String name;
    private String description;
    private AiAgentStatus status;
    private Role assignedRole;
    private int priority;
    private String systemPrompt;
    private String guardrails;
    private BigDecimal temperature;
    private int maxTokens;
    private int topK;
    private BigDecimal scoreThreshold;
    private String fallbackMessage;
    private boolean handoffEnabled;
    private BigDecimal handoffThreshold;
    private List<AiDatasetResponse> datasets;
    private int datasetCount;
    private Instant createdAt;
    private Instant updatedAt;
}

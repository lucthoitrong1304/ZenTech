package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class AiAgentRuntimeRequest {
    private RuntimeAgent agent;
    private Role role;
    private String message;
    private List<AiChatRespondRequest.HistoryMessage> history;
    private List<UUID> datasetIds;
    private Map<String, Object> businessContext;

    @Getter
    @Builder
    public static class RuntimeAgent {
        private UUID id;
        private String name;
        private String systemPrompt;
        private String guardrails;
        private BigDecimal temperature;
        private int maxTokens;
        private int topK;
        private BigDecimal scoreThreshold;
        private String fallbackMessage;
        private boolean handoffEnabled;
        private BigDecimal handoffThreshold;
    }
}

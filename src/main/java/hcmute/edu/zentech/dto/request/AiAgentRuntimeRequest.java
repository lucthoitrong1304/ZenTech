package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.ChatAttachmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentRuntimeRequest {
    private RuntimeAgent agent;
    private Role role;
    private String message;
    private List<HistoryMessage> history;
    private List<Attachment> attachments;
    private List<UUID> datasetIds;
    private Map<String, Object> businessContext;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryMessage {
        private String role;
        private String content;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String fileName;
        private String contentType;
        private ChatAttachmentType attachmentType;
        private String mediaUrl;
        private String contentBase64;
    }
}

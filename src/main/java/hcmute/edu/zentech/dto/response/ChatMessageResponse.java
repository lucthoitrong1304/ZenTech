package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ChatMessageType;
import hcmute.edu.zentech.model.ParticipantType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private UUID id;
    private UUID conversationId;
    private UUID participantId;
    private ParticipantType senderType;
    private UUID senderReferenceId;
    private ChatMessageType messageType;
    private String content;
    private List<ChatAttachmentResponse> attachments;
    private List<ChatRecommendedProductResponse> recommendedProducts;
    private Instant createdAt;
    private Instant deletedAt;
    private String traceId;
}

package hcmute.edu.zentech.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ChatConversationEventResponse {
    private String eventType;
    private UUID conversationId;
    private ConversationResponse conversation;
    private UUID actorAccountId;
    private String actorName;
    private List<UUID> notifiedAccountIds;
    private Instant occurredAt;
}

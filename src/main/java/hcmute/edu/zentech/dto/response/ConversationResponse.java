package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ConversationStatus;
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
public class ConversationResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private ConversationStatus status;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    private boolean archived;
    private Instant archivedAt;
    private List<ParticipantResponse> participants;
}

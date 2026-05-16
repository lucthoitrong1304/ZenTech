package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.TransferRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestResponse {
    private UUID id;
    private UUID conversationId;
    private UUID requestedBy;
    private UUID requestedTo;
    private String reason;
    private TransferRequestStatus status;
    private Instant createdAt;
    private Instant resolvedAt;
}

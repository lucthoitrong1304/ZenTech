package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTicketStatusResponse {
    private TicketStatus status;
    private String message;
    private Instant updatedAt;
    private Instant resolvedAt;
}

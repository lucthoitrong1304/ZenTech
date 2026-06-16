package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.TicketMessageSender;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageResponseDto {
    private UUID id;
    private TicketMessageSender sender;
    private String content;
    private Instant timestamp;
}

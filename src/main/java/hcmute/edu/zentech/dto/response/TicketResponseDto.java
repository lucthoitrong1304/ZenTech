package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.model.TicketStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDto {
    private UUID id;
    private String code;
    private UUID incidentId;
    private String incidentCode;
    private String title;
    private String description;
    private TicketPriority priority;
    private TicketStatus status;
    private UUID assigneeId;
    private String assigneeName;
    private String assigneeEmail;
    private UUID createdById;
    private String createdByName;
    private String createdByEmail;
    private java.util.List<String> affectedUserEmails;
    private Instant createdAt;
    private Instant resolvedAt;
    private String images;
}

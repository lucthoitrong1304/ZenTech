package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.model.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TicketCreateRequest {
    @NotBlank
    private String title;

    private String description;
    
    @NotNull
    private TicketPriority priority;
    
    @NotNull
    private TicketStatus status;
    
    private UUID assigneeId;
    
    private UUID incidentId;

    private String images;
}

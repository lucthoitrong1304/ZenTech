package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketMessageRequest {
    @NotBlank
    private String content;
}

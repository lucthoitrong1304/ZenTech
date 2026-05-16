package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TransferRequestCreateRequest {
    private UUID requestedTo;

    @Size(max = 2000, message = "reason must not exceed 2000 characters")
    private String reason;
}

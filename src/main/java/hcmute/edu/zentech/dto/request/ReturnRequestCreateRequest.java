package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReturnRequestCreateRequest {
    @NotBlank(message = "Reason is required")
    private String reason;

    private String details;

    private String proofFileKeys;
}

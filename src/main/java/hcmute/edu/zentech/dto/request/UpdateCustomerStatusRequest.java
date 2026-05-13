package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCustomerStatusRequest {
    @NotNull(message = "active is required")
    private Boolean active;
}

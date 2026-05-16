package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.TransferRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TransferRequestUpdateRequest {
    @NotNull(message = "status is required")
    private TransferRequestStatus status;
}

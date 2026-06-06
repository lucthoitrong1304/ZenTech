package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAccountStatusRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private Boolean active;
}

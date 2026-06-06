package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAccountRoleRequest {
    @NotNull(message = "Role không được để trống")
    private Role role;
}

package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.PermissionCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateRolePermissionsRequest {
    @NotNull(message = "Danh sách quyền không được để trống")
    private Set<PermissionCode> permissions;
}

package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class PermissionMatrixResponse {
    private List<Role> configurableRoles;
    private List<PermissionModuleResponse> modules;
    private Map<Role, Set<PermissionCode>> rolePermissions;
}

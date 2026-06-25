package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class CurrentPermissionsResponse {
    private Role role;
    private Set<PermissionCode> permissions;
}

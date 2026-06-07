package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AiAgentRolesRequest {
    @NotEmpty
    private Set<Role> assignedRoles;
    private boolean defaultForRole;
    private int priority;
}

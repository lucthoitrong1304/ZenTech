package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AiAgentRolesRequest {
    @jakarta.validation.constraints.NotNull
    private Role assignedRole;
    private int priority;
}

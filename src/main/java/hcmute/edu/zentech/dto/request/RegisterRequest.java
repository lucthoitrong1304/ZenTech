package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private Role role;
}

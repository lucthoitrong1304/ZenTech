package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    private String fullName;
    private Role role; // Mặc định thường là CUSTOMER
}
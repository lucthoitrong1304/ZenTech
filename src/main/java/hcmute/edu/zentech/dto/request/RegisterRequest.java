package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mat khau phai co it nhat 6 ky tu")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{6,}$",
            message = "Mat khau phai co it nhat 1 chu viet hoa va 1 ky tu dac biet"
    )
    private String password;

    private String fullName;
    private Role role; // Mặc định thường là CUSTOMER
}

package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank(message = "Token không được để trống")
    private String token; // Token ID từ Google
}
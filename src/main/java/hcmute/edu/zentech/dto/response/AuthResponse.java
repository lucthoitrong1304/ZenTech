package hcmute.edu.zentech.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Giúp ẩn các trường null khi trả về
public class AuthResponse {
    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String type = "Bearer";

    private String accountId; // Chứa UUID dạng String
    private String profileId;
    private String email;
    private String fullName;
    private List<String> roles;

    // Thêm cái này nếu muốn FE xử lý mượt hơn
    private Long expiresIn;

    // ---> THÊM CỜ NÀY VÀO NÈ <---
    private Boolean isPasswordSet; // Dùng Boolean (đối tượng) để lỡ có null thì nó bị ẩn đi nhờ @JsonInclude
}
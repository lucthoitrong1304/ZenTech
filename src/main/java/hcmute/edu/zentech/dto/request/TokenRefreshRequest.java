package hcmute.edu.zentech.dto.request;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}

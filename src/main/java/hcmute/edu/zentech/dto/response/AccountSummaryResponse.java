package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.Role;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AccountSummaryResponse {
    private UUID id;
    private String email;
    private Role role;
    private Boolean isActive;
    private Instant createdAt;
    private String displayName;
    private String imageUrl;
}

package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffectedUserDetailDto {
    private String userId;
    private String email;
    private String fullName;
    private String traceId;
    private Instant lastEventAt;
    private String lastEventUrl;
    private String avatarUrl;
}

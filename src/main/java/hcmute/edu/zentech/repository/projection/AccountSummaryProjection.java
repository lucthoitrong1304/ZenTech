package hcmute.edu.zentech.repository.projection;

import hcmute.edu.zentech.model.Role;
import java.time.Instant;
import java.util.UUID;

public interface AccountSummaryProjection {
    UUID getId();
    String getEmail();
    Role getRole();
    Boolean getIsActive();
    Instant getCreatedAt();
    String getDisplayName();
    String getImageUrl();
}

package hcmute.edu.zentech.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import hcmute.edu.zentech.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String title;
    private String content;
    @JsonProperty("isRead")
    @lombok.Getter(onMethod_ = @JsonIgnore)
    private boolean isRead;
    private NotificationType type;
    private UUID referenceId;
    private Instant createdAt;
}

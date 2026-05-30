package hcmute.edu.zentech.dto.response;

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
    private boolean isRead;
    private NotificationType type;
    private UUID referenceId;
    private Instant createdAt;
}

package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponseDto {
    private UUID id;
    private String operatorEmail;
    private String operatorFullName;
    private String operatorAvatar;
    private ActivityArea area;
    private String module;
    private ActivityAction action;
    private String actionLabel;
    private ActivitySeverity severity;
    private String targetType;
    private String targetId;
    private String targetLabel;
    private String target;
    private String summary;
    private String metadata;
    private String ipAddress;
    private String userAgent;
    private String traceId;
    private Instant timestamp;
}

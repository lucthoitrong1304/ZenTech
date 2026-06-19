package hcmute.edu.zentech.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTimelineAiLogItem {
    private String timestamp;
    private String operatorEmail;
    private String operatorRole;
    private String area;
    private String module;
    private String action;
    private String actionLabel;
    private String severity;
    private String targetType;
    private String targetId;
    private String targetLabel;
    private String summary;
    private String metadata;
    private String ipAddress;
    private String userAgent;
    private String traceId;
}

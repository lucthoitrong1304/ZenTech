package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivitySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTimelineSummaryRequest {
    private UUID userId;
    private String email;
    private Instant from;
    private Instant to;
    private ActivitySeverity severity;
    private String module;
    private ActivityAction action;
    private Integer size;
}

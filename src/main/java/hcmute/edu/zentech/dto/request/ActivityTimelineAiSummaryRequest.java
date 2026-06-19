package hcmute.edu.zentech.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTimelineAiSummaryRequest {
    private String userId;
    private String email;
    private Instant from;
    private Instant to;
    private String severity;
    private String module;
    private String action;
    private Integer size;
    private List<ActivityTimelineAiLogItem> logs;
}

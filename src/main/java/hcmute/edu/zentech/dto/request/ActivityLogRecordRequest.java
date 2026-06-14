package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityLogRecordRequest {
    @NotNull
    private ActivityAction action;

    @NotNull
    private ActivityArea area;

    @NotNull
    private ActivitySeverity severity;

    private String module;
    private String targetType;
    private String targetId;
    private String targetLabel;
    private String summary;
    private String metadata;
}

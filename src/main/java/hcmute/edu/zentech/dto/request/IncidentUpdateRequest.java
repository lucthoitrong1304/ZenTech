package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentUpdateRequest {
    private IncidentStatus status;
    private IncidentSeverity severity;
    private String assignee;
}

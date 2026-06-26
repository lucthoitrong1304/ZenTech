package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.IncidentCreationSource;
import hcmute.edu.zentech.model.IncidentStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class IssueIncidentLinkResponse {
    private UUID incidentId;
    private String incidentCode;
    private IncidentCreationSource creationSource;
    private IncidentStatus status;
}

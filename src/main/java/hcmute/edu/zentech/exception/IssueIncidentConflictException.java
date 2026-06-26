package hcmute.edu.zentech.exception;

import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import lombok.Getter;

@Getter
public class IssueIncidentConflictException extends RuntimeException {
    private final IncidentResponseDto incident;

    public IssueIncidentConflictException(IncidentResponseDto incident) {
        super("Issue already linked to Incident: " + incident.getCode());
        this.incident = incident;
    }
}

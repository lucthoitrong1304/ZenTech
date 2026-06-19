package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class IncidentCreateRequest {
    @NotBlank
    private String title;

    private String description;
    private String traceId;
    private String serviceName;
    private String apiPath;
    private String httpMethod;
    private Integer statusCode;
    private String errorMessage;
    private String stackTrace;
    
    @NotNull
    private IncidentSeverity severity;
    
    @NotNull
    private IncidentStatus status;
    
    private String assignee;
    private UUID userId;
    private String images;
}

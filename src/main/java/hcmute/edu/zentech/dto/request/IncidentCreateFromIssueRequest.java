package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IncidentCreateFromIssueRequest {
    @NotBlank
    private String issueSignature;

    @NotBlank
    private String title;

    private String serviceName;
    private String apiPath;
    private String httpMethod;
    private Integer statusCode;
    private String errorMessage;
    private String traceId;
    private String stackTrace;
    private Instant occurredAt;
    private UUID userId;

    @NotNull
    private IncidentSeverity severity;
}

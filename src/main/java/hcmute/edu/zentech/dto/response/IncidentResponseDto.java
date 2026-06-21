package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponseDto {
    private UUID id;
    private String code;
    private String traceId;
    private String serviceName;
    private String apiPath;
    private String httpMethod;
    private Integer statusCode;
    private String errorMessage;
    private String stackTrace;
    private IncidentSeverity severity;
    private IncidentStatus status;
    private Instant occurredAt;
    private Instant firstOccurredAt;
    private Instant createdAt;
    private Instant resolvedAt;
    private String assignee;
    private UUID userId;
    private String userEmail;
    private java.util.List<String> affectedUserEmails;
    private AiAnalysisResponseDto aiAnalysis;
    private String ticketCode;
    private java.util.List<OccurrenceDto> occurrences;
    private String images;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OccurrenceDto {
        private String traceId;
        private Instant occurredAt;
        private String userEmail;
    }
}

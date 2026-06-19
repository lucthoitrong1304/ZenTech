package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.AiAnalysisResponseDto;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.model.AiAnalysis;
import hcmute.edu.zentech.model.Incident;
import org.springframework.stereotype.Component;

@Component
public class IncidentMapper {

    public IncidentResponseDto toResponseDto(Incident incident, AiAnalysis aiAnalysis, String ticketCode) {
        return toResponseDto(incident, aiAnalysis, ticketCode, java.util.Collections.emptyList(), java.util.Collections.emptyList());
    }

    public IncidentResponseDto toResponseDto(Incident incident, AiAnalysis aiAnalysis, String ticketCode, java.util.List<String> affectedUserEmails) {
        return toResponseDto(incident, aiAnalysis, ticketCode, affectedUserEmails, java.util.Collections.emptyList());
    }

    public IncidentResponseDto toResponseDto(Incident incident, AiAnalysisResponseDto aiAnalysis, String ticketCode, 
                                             java.util.List<String> affectedUserEmails,
                                             java.util.List<IncidentResponseDto.OccurrenceDto> occurrences) {
        if (incident == null) {
            return null;
        }

        return IncidentResponseDto.builder()
                .id(incident.getId())
                .code(incident.getCode())
                .traceId(incident.getTraceId())
                .serviceName(incident.getServiceName())
                .apiPath(incident.getApiPath())
                .httpMethod(incident.getHttpMethod())
                .statusCode(incident.getStatusCode())
                .errorMessage(incident.getErrorMessage())
                .stackTrace(incident.getStackTrace())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .occurredAt(incident.getOccurredAt())
                .createdAt(incident.getCreatedAt())
                .resolvedAt(incident.getResolvedAt())
                .assignee(incident.getAssignee())
                .userId(incident.getUser() != null ? incident.getUser().getId() : null)
                .userEmail(incident.getUser() != null ? incident.getUser().getEmail() : null)
                .affectedUserEmails(affectedUserEmails)
                .aiAnalysis(aiAnalysis)
                .ticketCode(ticketCode)
                .occurrences(occurrences)
                .build();
    }

    public IncidentResponseDto toResponseDto(Incident incident, AiAnalysis aiAnalysis, String ticketCode, 
                                             java.util.List<String> affectedUserEmails,
                                             java.util.List<IncidentResponseDto.OccurrenceDto> occurrences) {
        if (incident == null) {
            return null;
        }

        return IncidentResponseDto.builder()
                .id(incident.getId())
                .code(incident.getCode())
                .traceId(incident.getTraceId())
                .serviceName(incident.getServiceName())
                .apiPath(incident.getApiPath())
                .httpMethod(incident.getHttpMethod())
                .statusCode(incident.getStatusCode())
                .errorMessage(incident.getErrorMessage())
                .stackTrace(incident.getStackTrace())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .occurredAt(incident.getOccurredAt())
                .createdAt(incident.getCreatedAt())
                .resolvedAt(incident.getResolvedAt())
                .assignee(incident.getAssignee())
                .userId(incident.getUser() != null ? incident.getUser().getId() : null)
                .userEmail(incident.getUser() != null ? incident.getUser().getEmail() : null)
                .affectedUserEmails(affectedUserEmails)
                .aiAnalysis(toAiAnalysisDto(aiAnalysis))
                .ticketCode(ticketCode)
                .occurrences(occurrences)
                .build();
    }

    public AiAnalysisResponseDto toAiAnalysisDto(AiAnalysis aiAnalysis) {
        if (aiAnalysis == null) {
            return null;
        }

        return AiAnalysisResponseDto.builder()
                .id(aiAnalysis.getId())
                .incidentId(aiAnalysis.getIncident().getId())
                .summary(aiAnalysis.getSummary())
                .rootCause(aiAnalysis.getRootCause())
                .severitySuggestion(aiAnalysis.getSeveritySuggestion())
                .solutionSuggestion(aiAnalysis.getSolutionSuggestion())
                .confidenceScore(aiAnalysis.getConfidenceScore())
                .createdAt(aiAnalysis.getCreatedAt())
                .build();
    }
}

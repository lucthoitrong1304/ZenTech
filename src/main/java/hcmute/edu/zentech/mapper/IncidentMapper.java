package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.AiAnalysisResponseDto;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;

import hcmute.edu.zentech.model.Incident;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IncidentMapper {
    private final hcmute.edu.zentech.service.R2StorageService r2StorageService;

    private String resolvePublicUrls(String imagesKeyStr) {
        if (imagesKeyStr == null || imagesKeyStr.isBlank() || r2StorageService == null) {
            return imagesKeyStr;
        }
        return java.util.Arrays.stream(imagesKeyStr.split(","))
                .map(String::trim)
                .map(r2StorageService::getPublicUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(java.util.stream.Collectors.joining(","));
    }



    public IncidentResponseDto toResponseDto(Incident incident, AiAnalysisResponseDto aiAnalysis, String ticketCode) {
        return toResponseDto(incident, aiAnalysis, ticketCode, java.util.Collections.emptyList(), java.util.Collections.emptyList());
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
                .images(resolvePublicUrls(incident.getImages()))
                .build();
    }

}


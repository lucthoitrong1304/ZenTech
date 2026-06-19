package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketMapper {
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

    public TicketResponseDto toResponseDto(Ticket ticket) {
        return toResponseDto(ticket, java.util.Collections.emptyList());
    }

    public TicketResponseDto toResponseDto(Ticket ticket, java.util.List<String> affectedUserEmails) {
        if (ticket == null) {
            return null;
        }

        return TicketResponseDto.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .incidentId(ticket.getIncident() != null ? ticket.getIncident().getId() : null)
                .incidentCode(ticket.getIncident() != null ? ticket.getIncident().getCode() : null)
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .assigneeId(ticket.getAssignee() != null ? ticket.getAssignee().getId() : null)
                .assigneeName(ticket.getAssignee() != null ? ticket.getAssignee().getEmail() : null)
                .assigneeEmail(ticket.getAssignee() != null ? ticket.getAssignee().getEmail() : null)
                .createdById(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : null)
                .createdByName(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getEmail() : null)
                .createdByEmail(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getEmail() : null)
                .affectedUserEmails(affectedUserEmails)
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .images(resolvePublicUrls(ticket.getImages()))
                .build();
    }
}

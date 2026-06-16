package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.TicketMessageResponseDto;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TicketMapper {

    public TicketResponseDto toResponseDto(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        List<TicketMessageResponseDto> messageDtos = new ArrayList<>();
        if (ticket.getMessages() != null) {
            messageDtos = ticket.getMessages().stream()
                    .map(this::toMessageDto)
                    .collect(Collectors.toList());
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
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .messages(messageDtos)
                .build();
    }

    public TicketMessageResponseDto toMessageDto(TicketMessage message) {
        if (message == null) {
            return null;
        }

        return TicketMessageResponseDto.builder()
                .id(message.getId())
                .sender(message.getSender())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }
}

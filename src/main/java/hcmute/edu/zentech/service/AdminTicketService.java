package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.TicketCreateRequest;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.dto.response.CustomerTicketStatusResponse;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.TicketMapper;
import hcmute.edu.zentech.mapper.IncidentMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final IncidentRepository incidentRepository;
    private final AccountUserRepository accountUserRepository;
    private final TicketMapper ticketMapper;
    private final IncidentMapper incidentMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActivityLogService activityLogService;
    private final TicketAudienceService ticketAudienceService;

    private synchronized String generateTicketCode() {
        long count = ticketRepository.countAllTickets();
        return String.format("TCK-%04d", count + 1);
    }

    public Page<TicketResponseDto> getTickets(
            TicketStatus status,
            TicketPriority priority,
            String assigneeEmail,
            Instant startDate,
            Instant endDate,
            String search,
            Pageable pageable
    ) {
        Page<Ticket> tickets = ticketRepository.searchTickets(
                status, priority, assigneeEmail, startDate, endDate, search, pageable
        );
        return tickets.map(ticket -> ticketMapper.toResponseDto(ticket, ticketAudienceService.getAffectedUserEmails(ticket)));
    }

    public TicketResponseDto getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y Ticket há»— trá»£ vá»›i ID: " + id));
        return ticketMapper.toResponseDto(ticket, ticketAudienceService.getAffectedUserEmails(ticket));
    }

    private void notifyCustomerTicketStatusChanged() {
        try {
            messagingTemplate.convertAndSend("/topic/customer.tickets", java.util.Map.of("type", "TICKET_UPDATED"));
        } catch (Exception e) {
            log.error("Failed to send customer ticket status refresh notification", e);
        }
    }
    @Transactional
    public TicketResponseDto createTicket(TicketCreateRequest request, UUID createdById) {
        AccountUser createdBy = null;
        if (createdById != null) {
            createdBy = accountUserRepository.findById(createdById).orElse(null);
        }

        AccountUser assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = accountUserRepository.findById(request.getAssigneeId()).orElse(null);
        }

        Incident incident = null;
        if (request.getIncidentId() != null) {
            incident = incidentRepository.findById(request.getIncidentId())
                    .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y sá»± cá»‘ vá»›i ID: " + request.getIncidentId()));
        }

        // Kiá»ƒm tra xem Incident nÃ y Ä‘Ã£ cÃ³ Ticket chÆ°a
        if (incident != null) {
            ticketRepository.findByIncidentId(incident.getId()).ifPresent(existingTicket -> {
                throw new IllegalStateException("Sá»± cá»‘ nÃ y Ä‘Ã£ Ä‘Æ°á»£c liÃªn káº¿t vá»›i Ticket: " + existingTicket.getCode());
            });
            if (incident.getUser() != null) {
                createdBy = incident.getUser();
            }
            if (incident.getAssignee() != null && !incident.getAssignee().isBlank() && assignee == null) {
                assignee = accountUserRepository.findByEmailIgnoreCase(incident.getAssignee().trim()).orElse(null);
            }
        }

        String images = request.getImages();
        if ((images == null || images.isBlank()) && incident != null) {
            images = incident.getImages();
        }

        Ticket ticket = Ticket.builder()
                .code(generateTicketCode())
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(request.getStatus())
                .assignee(assignee)
                .createdBy(createdBy)
                .incident(incident)
                .images(images)
                .createdAt(Instant.now())
                .build();

        // Náº¿u táº¡o Ticket tá»« Incident vÃ  gÃ¡n tráº¡ng thÃ¡i khÃ¡c RESOLVED/CLOSED, tá»± Ä‘á»™ng chuyá»ƒn Incident sang INVESTIGATING
        if (incident != null && request.getStatus() != TicketStatus.RESOLVED && request.getStatus() != TicketStatus.CLOSED) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
            if (assignee != null) {
                // Äá»“ng bá»™ tÃªn ngÆ°á»i phá»¥ trÃ¡ch cho Incident
                incident.setAssignee(assignee.getEmail());
            }
            incidentRepository.save(incident);
            
            // Gá»­i websocket sá»± cá»‘ cáº­p nháº­t
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, ticket.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        Ticket saved = ticketRepository.save(ticket);
        
        // Ghi log hoáº¡t Ä‘á»™ng táº¡o ticket má»›i
        activityLogService.log(
                createdById,
                ActivityArea.ADMIN,
                "TICKET",
                ActivityAction.CREATE_TICKET,
                ActivitySeverity.INFO,
                "Ticket",
                saved.getId().toString(),
                saved.getCode(),
                "Táº¡o Ticket há»— trá»£ má»›i: " + saved.getCode(),
                null
        );

        // Náº¿u táº¡o Ticket tá»« Incident vÃ  gÃ¡n tráº¡ng thÃ¡i khÃ¡c RESOLVED/CLOSED, tá»± Ä‘á»™ng chuyá»ƒn Incident sang INVESTIGATING
        if (incident != null && request.getStatus() != TicketStatus.RESOLVED && request.getStatus() != TicketStatus.CLOSED) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
            if (assignee != null) {
                // Äá»“ng bá»™ tÃªn ngÆ°á»i phá»¥ trÃ¡ch cho Incident
                incident.setAssignee(assignee.getEmail());
            }
            incidentRepository.save(incident);
            
            // Gá»­i websocket sá»± cá»‘ cáº­p nháº­t
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, ticket.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved, ticketAudienceService.getAffectedUserEmails(saved));
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
            notifyCustomerTicketStatusChanged();
        } catch (Exception e) {
            log.error("Failed to send ticket create websocket notification", e);
        }
        return response;
    }

    @Transactional
    public TicketResponseDto updateTicketStatus(UUID id, TicketStatus status) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y Ticket há»— trá»£ vá»›i ID: " + id));

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(status);
        boolean incidentUpdated = false;
        Incident incidentToNotify = null;

        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());

            // Äá»“ng bá»™ tráº¡ng thÃ¡i RESOLVED cho Incident liÃªn káº¿t
            if (ticket.getIncident() != null) {
                Incident incident = ticket.getIncident();
                if (incident.getStatus() != IncidentStatus.RESOLVED) {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(Instant.now());
                    incidentRepository.save(incident);
                    log.info("Automatically resolved linked incident: {}", incident.getCode());

                    // Ghi log hoáº¡t Ä‘á»™ng Ä‘á»“ng bá»™
                    activityLogService.log(
                            null,
                            ActivityArea.SYSTEM,
                            "INCIDENT",
                            ActivityAction.RESOLVE_INCIDENT,
                            ActivitySeverity.INFO,
                            "Incident",
                            incident.getId().toString(),
                            incident.getCode(),
                            "Há»‡ thá»‘ng tá»± Ä‘á»™ng Ä‘Ã³ng sá»± cá»‘ liÃªn káº¿t: " + incident.getCode() + " do Ä‘Ã³ng Ticket",
                            null
                    );

                    incidentUpdated = true;
                    incidentToNotify = incident;
                }
            }
        } else {
            ticket.setResolvedAt(null);
            
            // Náº¿u má»Ÿ láº¡i Ticket, cÅ©ng má»Ÿ láº¡i Incident
            if (ticket.getIncident() != null) {
                Incident incident = ticket.getIncident();
                if (incident.getStatus() == IncidentStatus.RESOLVED) {
                    incident.setStatus(IncidentStatus.INVESTIGATING);
                    incident.setResolvedAt(null);
                    incidentRepository.save(incident);
                    log.info("Automatically reopened linked incident: {}", incident.getCode());

                    // Ghi log hoáº¡t Ä‘á»™ng Ä‘á»“ng bá»™
                    activityLogService.log(
                            null,
                            ActivityArea.SYSTEM,
                            "INCIDENT",
                            ActivityAction.UPDATE_INCIDENT,
                            ActivitySeverity.INFO,
                            "Incident",
                            incident.getId().toString(),
                            incident.getCode(),
                            "Há»‡ thá»‘ng tá»± Ä‘á»™ng má»Ÿ láº¡i sá»± cá»‘ liÃªn káº¿t: " + incident.getCode() + " do má»Ÿ láº¡i Ticket",
                            null
                    );

                    incidentUpdated = true;
                    incidentToNotify = incident;
                }
            }
        }

        Ticket saved = ticketRepository.save(ticket);

        // Ghi log hoáº¡t Ä‘á»™ng Ä‘á»•i tráº¡ng thÃ¡i Ticket
        if (status != oldStatus) {
            ActivityAction act = (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) ? ActivityAction.CLOSE_TICKET : ActivityAction.UPDATE_TICKET_STATUS;
            activityLogService.log(
                    null,
                    ActivityArea.ADMIN,
                    "TICKET",
                    act,
                    ActivitySeverity.INFO,
                    "Ticket",
                    saved.getId().toString(),
                    saved.getCode(),
                    "Cáº­p nháº­t tráº¡ng thÃ¡i Ticket " + saved.getCode() + " thÃ nh " + status,
                    null
            );
        }

        // Notify incident update if changed
        if (incidentUpdated && incidentToNotify != null) {
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incidentToNotify, null, saved.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved, ticketAudienceService.getAffectedUserEmails(saved));
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
            notifyCustomerTicketStatusChanged();
        } catch (Exception e) {
            log.error("Failed to send ticket status update websocket notification", e);
        }
        return response;
    }

    @Transactional
    public TicketResponseDto updateTicketAssignee(UUID id, UUID assigneeId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y Ticket há»— trá»£ vá»›i ID: " + id));

        AccountUser oldAssignee = ticket.getAssignee();
        AccountUser assignee = null;
        if (assigneeId != null) {
            assignee = accountUserRepository.findById(assigneeId)
                    .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i dÃ¹ng vá»›i ID: " + assigneeId));
        }

        ticket.setAssignee(assignee);
        Ticket saved = ticketRepository.save(ticket);

        // Ghi log hoáº¡t Ä‘á»™ng phÃ¢n cÃ´ng ticket
        if (!java.util.Objects.equals(oldAssignee, assignee)) {
            activityLogService.log(
                    null,
                    ActivityArea.ADMIN,
                    "TICKET",
                    ActivityAction.ASSIGN_TICKET,
                    ActivitySeverity.INFO,
                    "Ticket",
                    saved.getId().toString(),
                    saved.getCode(),
                    "PhÃ¢n cÃ´ng Ticket " + saved.getCode() + " cho " + (assignee == null ? "chÆ°a phÃ¢n cÃ´ng" : assignee.getEmail()),
                    null
            );
        }

        // Äá»“ng bá»™ ngÆ°á»£c láº¡i cho Incident náº¿u cÃ³ liÃªn káº¿t
        if (saved.getIncident() != null) {
            Incident incident = saved.getIncident();
            String newAssigneeEmail = assignee != null ? assignee.getEmail() : null;
            if (!java.util.Objects.equals(incident.getAssignee(), newAssigneeEmail)) {
                incident.setAssignee(newAssigneeEmail);
                incidentRepository.save(incident);
                log.info("Synchronized assignee from Ticket {} to Incident {}", saved.getCode(), incident.getCode());

                // Ghi log hoáº¡t Ä‘á»™ng Ä‘á»“ng bá»™
                activityLogService.log(
                        null,
                        ActivityArea.SYSTEM,
                        "INCIDENT",
                        ActivityAction.UPDATE_INCIDENT,
                        ActivitySeverity.INFO,
                        "Incident",
                        incident.getId().toString(),
                        incident.getCode(),
                        "Há»‡ thá»‘ng tá»± Ä‘á»™ng phÃ¢n cÃ´ng sá»± cá»‘ liÃªn káº¿t " + incident.getCode() + " cho " + (newAssigneeEmail == null ? "chÆ°a phÃ¢n cÃ´ng" : newAssigneeEmail),
                        null
                );

                // Gá»­i websocket cáº­p nháº­t incident
                try {
                    IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, saved.getCode());
                    messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
                } catch (Exception e) {
                    log.error("Failed to send incident update websocket notification", e);
                }
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved, ticketAudienceService.getAffectedUserEmails(saved));
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
            notifyCustomerTicketStatusChanged();
        } catch (Exception e) {
            log.error("Failed to send ticket assignee update websocket notification", e);
        }
        return response;
    }
}



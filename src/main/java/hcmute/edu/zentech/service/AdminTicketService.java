package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.TicketCreateRequest;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.TicketMapper;
import hcmute.edu.zentech.mapper.IncidentMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final IncidentRepository incidentRepository;
    private final AccountUserRepository accountUserRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketMapper ticketMapper;
    private final IncidentMapper incidentMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private synchronized String generateTicketCode() {
        long count = ticketRepository.countAllTickets();
        return String.format("TCK-%04d", count + 1);
    }

    public List<TicketResponseDto> getTickets(TicketStatus status) {
        List<Ticket> tickets;
        if (status == null) {
            tickets = ticketRepository.findAll();
            // Sort by createdAt desc
            tickets.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        } else {
            tickets = ticketRepository.findByStatusOrderByCreatedAtDesc(status);
        }

        return tickets.stream()
                .map(ticketMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    public TicketResponseDto getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + id));
        return ticketMapper.toResponseDto(ticket);
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
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + request.getIncidentId()));
        }

        // Kiểm tra xem Incident này đã có Ticket chưa
        if (incident != null) {
            ticketRepository.findByIncidentId(incident.getId()).ifPresent(existingTicket -> {
                throw new IllegalStateException("Sự cố này đã được liên kết với Ticket: " + existingTicket.getCode());
            });
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
                .createdAt(Instant.now())
                .messages(new ArrayList<>())
                .build();

        // Nếu tạo Ticket từ Incident và gán trạng thái khác RESOLVED/CLOSED, tự động chuyển Incident sang INVESTIGATING
        if (incident != null && request.getStatus() != TicketStatus.RESOLVED && request.getStatus() != TicketStatus.CLOSED) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
            if (assignee != null) {
                // Đồng bộ tên người phụ trách cho Incident
                incident.setAssignee(assignee.getEmail());
            }
            incidentRepository.save(incident);
            
            // Gửi websocket sự cố cập nhật
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, ticket.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        Ticket saved = ticketRepository.save(ticket);
        TicketResponseDto response = ticketMapper.toResponseDto(saved);
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
        } catch (Exception e) {
            log.error("Failed to send ticket create websocket notification", e);
        }
        return response;
    }

    @Transactional
    public TicketResponseDto updateTicketStatus(UUID id, TicketStatus status) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + id));

        ticket.setStatus(status);
        boolean incidentUpdated = false;
        Incident incidentToNotify = null;

        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());

            // Đồng bộ trạng thái RESOLVED cho Incident liên kết
            if (ticket.getIncident() != null) {
                Incident incident = ticket.getIncident();
                if (incident.getStatus() != IncidentStatus.RESOLVED) {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(Instant.now());
                    incidentRepository.save(incident);
                    log.info("Automatically resolved linked incident: {}", incident.getCode());
                    incidentUpdated = true;
                    incidentToNotify = incident;
                }
            }
        } else {
            ticket.setResolvedAt(null);
            
            // Nếu mở lại Ticket, cũng mở lại Incident
            if (ticket.getIncident() != null) {
                Incident incident = ticket.getIncident();
                if (incident.getStatus() == IncidentStatus.RESOLVED) {
                    incident.setStatus(IncidentStatus.INVESTIGATING);
                    incident.setResolvedAt(null);
                    incidentRepository.save(incident);
                    log.info("Automatically reopened linked incident: {}", incident.getCode());
                    incidentUpdated = true;
                    incidentToNotify = incident;
                }
            }
        }

        Ticket saved = ticketRepository.save(ticket);

        // Notify incident update if changed
        if (incidentUpdated && incidentToNotify != null) {
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incidentToNotify, null, saved.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved);
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
        } catch (Exception e) {
            log.error("Failed to send ticket status update websocket notification", e);
        }
        return response;
    }

    @Transactional
    public TicketResponseDto addMessage(UUID ticketId, String content, TicketMessageSender sender) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + ticketId));

        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .content(content)
                .timestamp(Instant.now())
                .build();

        ticketMessageRepository.save(message);

        // Nạp lại ticket để lấy danh sách tin nhắn đầy đủ
        Ticket updatedTicket = ticketRepository.findById(ticketId).orElse(ticket);
        TicketResponseDto response = ticketMapper.toResponseDto(updatedTicket);
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
        } catch (Exception e) {
            log.error("Failed to send ticket message websocket notification", e);
        }
        return response;
    }
}

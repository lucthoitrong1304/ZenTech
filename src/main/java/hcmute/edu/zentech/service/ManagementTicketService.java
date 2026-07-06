package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.CustomerTicketStatusResponse;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.TicketMapper;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.TicketRepository;
import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagementTicketService {

    private final TicketRepository ticketRepository;
    private final AccountUserRepository accountUserRepository;
    private final TicketMapper ticketMapper;
    private final TicketAudienceService ticketAudienceService;
    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public Page<TicketResponseDto> getTickets(
            TicketStatus status,
            TicketPriority priority,
            String assigneeEmail,
            String customerEmail,
            Instant startDate,
            Instant endDate,
            String search,
            Pageable pageable
    ) {
        if (customerEmail == null || customerEmail.isBlank()) {
            Page<Ticket> tickets = ticketRepository.searchTickets(
                    status, priority, assigneeEmail, startDate, endDate, search, pageable
            );
            return tickets.map(this::toResponseDto);
        }

        String normalizedCustomerEmail = customerEmail.trim().toLowerCase();
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();

        List<TicketResponseDto> filtered = new java.util.ArrayList<>(ticketRepository.findAll(Sort.by(
                        Sort.Order.asc("status"),
                        Sort.Order.desc("createdAt")
                )).stream()
                .filter(ticket -> status == null || ticket.getStatus() == status)
                .filter(ticket -> priority == null || ticket.getPriority() == priority)
                .filter(ticket -> matchesAssignee(ticket, assigneeEmail))
                .filter(ticket -> startDate == null || !ticket.getCreatedAt().isBefore(startDate))
                .filter(ticket -> endDate == null || !ticket.getCreatedAt().isAfter(endDate))
                .filter(ticket -> normalizedSearch.isBlank() || matchesTicketSearch(ticket, normalizedSearch))
                .filter(ticket -> ticketAudienceService.matchesCustomerEmail(ticket, normalizedCustomerEmail))
                .map(this::toResponseDto)
                .toList());

        if (status == null || status == TicketStatus.OPEN) {
            List<Incident> activeIncidents = incidentRepository.findAll().stream()
                    .filter(incident -> incident.getUser() != null && normalizedCustomerEmail.equalsIgnoreCase(incident.getUser().getEmail()))
                    .filter(incident -> incident.getStatus() == IncidentStatus.OPEN || incident.getStatus() == IncidentStatus.INVESTIGATING)
                    .filter(incident -> ticketRepository.findByIncidentId(incident.getId()).isEmpty())
                    .sorted(Comparator.comparing(Incident::getOccurredAt).reversed())
                    .toList();

            for (Incident incident : activeIncidents) {
                String title = incident.getErrorMessage();
                if (title == null || title.isBlank()) {
                    title = "Lỗi hệ thống (" + incident.getStatusCode() + ")";
                }

                TicketResponseDto incidentDto = TicketResponseDto.builder()
                        .id(incident.getId())
                        .code(incident.getCode())
                        .incidentId(incident.getId())
                        .incidentCode(incident.getCode())
                        .title("Sự cố hệ thống: " + title)
                        .description(incident.getErrorMessage())
                        .priority(incident.getSeverity() == IncidentSeverity.CRITICAL ? TicketPriority.HIGH : TicketPriority.MEDIUM)
                        .status(TicketStatus.OPEN)
                        .createdAt(incident.getOccurredAt())
                        .createdByEmail(incident.getUser() != null ? incident.getUser().getEmail() : null)
                        .createdByName(incident.getUser() != null ? ticketMapper.resolveDisplayName(incident.getUser()) : null)
                        .affectedUserEmails(List.of(normalizedCustomerEmail))
                        .build();

                filtered.add(0, incidentDto);
            }
        }

        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public TicketResponseDto getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + id));
        return toResponseDto(ticket);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerTicketStatusResponse> getCustomerSafeTicketStatus(UUID currentUserId) {
        if (currentUserId == null) {
            return Optional.empty();
        }

        AccountUser currentUser = accountUserRepository.findById(currentUserId).orElse(null);
        if (currentUser == null || currentUser.getEmail() == null || currentUser.getEmail().isBlank()) {
            return Optional.empty();
        }

        String normalizedEmail = currentUser.getEmail().trim().toLowerCase();
        List<Ticket> customerTickets = ticketRepository.findAll().stream()
                .filter(ticket -> ticketAudienceService.matchesCustomerEmail(ticket, normalizedEmail))
                .toList();

        Optional<Ticket> primaryTicket = customerTickets.stream()
                .max(Comparator
                        .comparingInt((Ticket ticket) -> ticketStatusRank(ticket.getStatus()))
                        .thenComparing(this::ticketUpdatedAt));

        if (primaryTicket.isPresent()) {
            Ticket ticket = primaryTicket.get();
            if (ticket.getStatus() != TicketStatus.CLOSED) {
                return Optional.of(CustomerTicketStatusResponse.builder()
                        .ticketCode(ticket.getCode())
                        .status(ticket.getStatus())
                        .message(buildCustomerTicketMessage(ticket.getStatus(), ticket.getTitle()))
                        .updatedAt(ticketUpdatedAt(ticket))
                        .resolvedAt(ticket.getResolvedAt())
                        .build());
            }
        }

        List<Incident> activeIncidents = incidentRepository.findAll().stream()
                .filter(incident -> incident.getUser() != null && normalizedEmail.equalsIgnoreCase(incident.getUser().getEmail()))
                .filter(incident -> incident.getStatus() == IncidentStatus.OPEN || incident.getStatus() == IncidentStatus.INVESTIGATING)
                .filter(incident -> ticketRepository.findByIncidentId(incident.getId()).isEmpty())
                .sorted(Comparator.comparing(Incident::getOccurredAt).reversed())
                .toList();

        if (!activeIncidents.isEmpty()) {
            Incident latestIncident = activeIncidents.get(0);
            String title = latestIncident.getErrorMessage();
            if (title == null || title.isBlank()) {
                title = "Lỗi hệ thống (" + latestIncident.getStatusCode() + ")";
            }
            return Optional.of(CustomerTicketStatusResponse.builder()
                    .ticketCode(latestIncident.getCode())
                    .status(TicketStatus.OPEN)
                    .message("Hệ thống ghi nhận sự cố: \"" + title + "\". Chúng tôi đang tiến hành kiểm tra.")
                    .updatedAt(latestIncident.getOccurredAt())
                    .build());
        }

        if (primaryTicket.isPresent()) {
            Ticket ticket = primaryTicket.get();
            return Optional.of(CustomerTicketStatusResponse.builder()
                    .ticketCode(ticket.getCode())
                    .status(ticket.getStatus())
                    .message(buildCustomerTicketMessage(ticket.getStatus(), ticket.getTitle()))
                    .updatedAt(ticketUpdatedAt(ticket))
                    .resolvedAt(ticket.getResolvedAt())
                    .build());
        }

        return Optional.empty();
    }

    private TicketResponseDto toResponseDto(Ticket ticket) {
        return ticketMapper.toResponseDto(ticket, ticketAudienceService.getAffectedUserEmails(ticket));
    }

    private boolean matchesAssignee(Ticket ticket, String assigneeEmail) {
        if (assigneeEmail == null || assigneeEmail.isBlank() || "ALL".equalsIgnoreCase(assigneeEmail)) {
            return true;
        }
        if ("UNASSIGNED".equalsIgnoreCase(assigneeEmail)) {
            return ticket.getAssignee() == null;
        }
        return ticket.getAssignee() != null && assigneeEmail.equalsIgnoreCase(ticket.getAssignee().getEmail());
    }

    private boolean matchesTicketSearch(Ticket ticket, String search) {
        return containsIgnoreCase(ticket.getCode(), search)
                || containsIgnoreCase(ticket.getTitle(), search)
                || containsIgnoreCase(ticket.getDescription(), search)
                || (ticket.getCreatedBy() != null && containsIgnoreCase(ticket.getCreatedBy().getEmail(), search))
                || (ticket.getAssignee() != null && containsIgnoreCase(ticket.getAssignee().getEmail(), search))
                || (ticket.getIncident() != null && containsIgnoreCase(ticket.getIncident().getCode(), search));
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private int ticketStatusRank(TicketStatus status) {
        if (status == TicketStatus.OPEN || status == TicketStatus.IN_PROGRESS) {
            return 2;
        }
        if (status == TicketStatus.RESOLVED) {
            return 1;
        }
        return 0;
    }

    private Instant ticketUpdatedAt(Ticket ticket) {
        if (ticket.getResolvedAt() != null) {
            return ticket.getResolvedAt();
        }
        return ticket.getCreatedAt() != null ? ticket.getCreatedAt() : Instant.EPOCH;
    }

    private String buildCustomerTicketMessage(TicketStatus status, String title) {
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            return "Sự cố \"" + title + "\" đã được khắc phục. Bạn có thể thử lại hoặc nhắn nhân viên nếu vẫn gặp lỗi.";
        }
        return "Hệ thống ghi nhận sự cố: \"" + title + "\". Chúng tôi đang tiến hành kiểm tra.";
    }
}

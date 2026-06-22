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

        List<TicketResponseDto> filtered = ticketRepository.findAll(Sort.by(
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
                .toList();

        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public TicketResponseDto getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kh?ng t?m th?y Ticket h? tr? v?i ID: " + id));
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
        return ticketRepository.findAll().stream()
                .filter(ticket -> ticketAudienceService.matchesCustomerEmail(ticket, normalizedEmail))
                .max(Comparator
                        .comparingInt((Ticket ticket) -> ticketStatusRank(ticket.getStatus()))
                        .thenComparing(this::ticketUpdatedAt))
                .map(ticket -> CustomerTicketStatusResponse.builder()
                        .status(ticket.getStatus())
                        .message(buildCustomerTicketMessage(ticket.getStatus()))
                        .updatedAt(ticketUpdatedAt(ticket))
                        .resolvedAt(ticket.getResolvedAt())
                        .build());
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

    private String buildCustomerTicketMessage(TicketStatus status) {
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            return "S? c? ?? ???c kh?c ph?c. B?n c? th? th? l?i ho?c nh?n nh?n vi?n n?u v?n g?p l?i.";
        }
        return "ZenTech ?? ghi nh?n s? c? b?n g?p ph?i v? ?ang x? l?.";
    }
}

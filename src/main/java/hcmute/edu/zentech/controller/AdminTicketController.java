package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.TicketCreateRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.AdminTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@Slf4j
public class AdminTicketController {

    private final AdminTicketService ticketService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketResponseDto>>> getTickets(
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "priority", required = false) TicketPriority priority,
            @RequestParam(value = "assigneeEmail", required = false) String assigneeEmail,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        log.info("Request to get support tickets, status={}, page={}, size={}", status, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("status"),
                Sort.Order.desc("createdAt")
        ));
        Page<TicketResponseDto> ticketPage = ticketService.getTickets(
                status, priority, assigneeEmail, startDate, endDate, search, pageable
            );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(ticketPage, ticketPage.getContent())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponseDto>> getTicketById(@PathVariable("id") UUID id) {
        log.info("Request to get ticket by id: {}", id);
        return ResponseEntity.ok(ApiResponse.success(ticketService.getTicketById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TicketResponseDto>> createTicket(
            @Valid @RequestBody TicketCreateRequest request
    ) {
        UUID currentUserId = SecurityContextUtils.getCurrentUserId();
        log.info("Request to create support ticket from incident, createdBy={}", currentUserId);
        return ResponseEntity.ok(ApiResponse.success(ticketService.createTicket(request, currentUserId)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TicketResponseDto>> updateTicketStatus(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> payload
    ) {
        String statusStr = payload.get("status");
        log.info("Request to update ticket {} status to {}", id, statusStr);
        TicketStatus status = TicketStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(ticketService.updateTicketStatus(id, status)));
    }

    @PatchMapping("/{id}/assignee")
    public ResponseEntity<ApiResponse<TicketResponseDto>> updateTicketAssignee(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> payload
    ) {
        String assigneeIdStr = payload.get("assigneeId");
        log.info("Request to update ticket {} assignee to {}", id, assigneeIdStr);
        UUID assigneeId = (assigneeIdStr == null || assigneeIdStr.trim().isEmpty() || "UNASSIGNED".equalsIgnoreCase(assigneeIdStr))
                ? null
                : UUID.fromString(assigneeIdStr.trim());
        return ResponseEntity.ok(ApiResponse.success(ticketService.updateTicketAssignee(id, assigneeId)));
    }
}

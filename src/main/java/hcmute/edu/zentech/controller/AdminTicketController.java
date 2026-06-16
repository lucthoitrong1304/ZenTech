package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.TicketCreateRequest;
import hcmute.edu.zentech.dto.request.TicketMessageRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.TicketMessageSender;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.AdminTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@Slf4j
public class AdminTicketController {

    private final AdminTicketService ticketService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TicketResponseDto>>> getTickets(
            @RequestParam(value = "status", required = false) TicketStatus status
    ) {
        log.info("Request to get support tickets, status={}", status);
        return ResponseEntity.ok(ApiResponse.success(ticketService.getTickets(status)));
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

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<TicketResponseDto>> addMessage(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TicketMessageRequest request
    ) {
        log.info("Request to send reply in ticket: {}", id);
        // Admin panel, so sender is always SUPPORT_AGENT
        return ResponseEntity.ok(ApiResponse.success(ticketService.addMessage(id, request.getContent(), TicketMessageSender.SUPPORT_AGENT)));
    }
}

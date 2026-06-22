package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CustomerTicketStatusResponse;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.ManagementTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/tickets")
@RequiredArgsConstructor
public class CustomerTicketStatusController {

    private final ManagementTicketService ticketService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<CustomerTicketStatusResponse>> getCurrentCustomerTicketStatus() {
        UUID currentUserId = SecurityContextUtils.getCurrentUserId();
        Optional<CustomerTicketStatusResponse> status = ticketService.getCustomerSafeTicketStatus(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(status.orElse(null)));
    }
}

package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.service.ManagementTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/tickets")
@RequiredArgsConstructor
public class ManagementTicketController {

    private final ManagementTicketService ticketService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketResponseDto>>> getTickets(
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "priority", required = false) TicketPriority priority,
            @RequestParam(value = "assigneeEmail", required = false) String assigneeEmail,
            @RequestParam(value = "customerEmail", required = false) String customerEmail,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("status"),
                Sort.Order.desc("createdAt")
        ));
        Page<TicketResponseDto> ticketPage = ticketService.getTickets(
                status, priority, assigneeEmail, customerEmail, startDate, endDate, search, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(ticketPage, ticketPage.getContent())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponseDto>> getTicketById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getTicketById(id)));
    }
}

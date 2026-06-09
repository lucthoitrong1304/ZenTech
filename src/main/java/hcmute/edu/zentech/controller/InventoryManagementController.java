package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.InventoryAdjustmentRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.InventorySummaryResponse;
import hcmute.edu.zentech.dto.response.InventoryTransactionResponse;
import hcmute.edu.zentech.dto.response.InventoryStatsResponse;
import hcmute.edu.zentech.dto.response.InventoryTransactionStatsResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.InventoryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/inventory")
@RequiredArgsConstructor
public class InventoryManagementController {
    private final InventoryManagementService inventoryManagementService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PageResponse<InventorySummaryResponse>>> getInventorySummary(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "productName,asc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "all") String stockStatus
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryManagementService.getInventorySummary(page, size, sort, keyword, stockStatus)
        ));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> getTransactionLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryManagementService.getTransactionLogs(page, size, sort, keyword, type, employeeId, reason, startDate, endDate)
        ));
    }

    @GetMapping("/transactions/stats")
    public ResponseEntity<ApiResponse<InventoryTransactionStatsResponse>> getTransactionStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryManagementService.getTransactionStats(keyword, type, employeeId, reason, startDate, endDate)
        ));
    }

    @PostMapping("/adjust")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> adjustStock(
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        UUID employeeId = SecurityContextUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                inventoryManagementService.adjustStock(request, employeeId)
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<InventoryStatsResponse>> getInventoryStats() {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryManagementService.getInventoryStats()
        ));
    }

    @GetMapping("/ai-recommendations")
    public ResponseEntity<ApiResponse<hcmute.edu.zentech.dto.response.AiInventoryRecommendResponse>> getAiRecommendations() {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryManagementService.getAiRecommendations()
        ));
    }
}

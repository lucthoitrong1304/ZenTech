package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.UpdateCustomerStatusRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.service.OwnerCustomerManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/owner/customers")
@RequiredArgsConstructor
public class OwnerCustomerManagementController {

    private final OwnerCustomerManagementService ownerCustomerManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerSummaryResponse>>> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "registeredAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerCustomerManagementService.getCustomers(page, size, sort, keyword, active)
        ));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getCustomerDetail(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.success(ownerCustomerManagementService.getCustomerDetail(customerId)));
    }

    @PatchMapping("/{customerId}/status")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> updateCustomerStatus(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerCustomerManagementService.updateCustomerStatus(customerId, request.getActive())
        ));
    }

    @GetMapping("/{customerId}/orders")
    public ResponseEntity<ApiResponse<PageResponse<CustomerOrderHistoryResponse>>> getCustomerOrders(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerCustomerManagementService.getCustomerOrders(customerId, page, size, sort)
        ));
    }
}

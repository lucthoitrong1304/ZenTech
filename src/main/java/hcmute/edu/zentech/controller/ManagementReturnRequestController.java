package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.ReturnRequestResponse;
import hcmute.edu.zentech.service.ManagementReturnRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/return-requests")
@RequiredArgsConstructor
public class ManagementReturnRequestController {

    private final ManagementReturnRequestService returnRequestService;

    @GetMapping
    @PreAuthorize("hasAuthority('RETURN_VIEW')")
    public ResponseEntity<ApiResponse<List<ReturnRequestResponse>>> getReturnRequests() {
        return ResponseEntity.ok(ApiResponse.success(returnRequestService.getReturnRequests()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RETURN_VIEW')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getReturnRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnRequestService.getReturnRequest(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> approveReturnRequest(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean resellable
    ) {
        return ResponseEntity.ok(ApiResponse.success(returnRequestService.approveReturnRequest(id, resellable)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> rejectReturnRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(returnRequestService.rejectReturnRequest(id)));
    }
}

package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.ShiftSwapRequestResponse;
import hcmute.edu.zentech.mapper.ApprovalRequestMapper;
import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.ShiftSwapRequest;
import hcmute.edu.zentech.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShiftSwapController {
    private final ApprovalService approvalService;
    private final ApprovalRequestMapper approvalRequestMapper;

    @PostMapping("/api/schedules/swaps")
    public ResponseEntity<ApiResponse<ShiftSwapRequestResponse>> requestSwap(
            @RequestBody ShiftSwapRequest request
    ) {
        ShiftSwapRequest saved = approvalService.requestShiftSwap(request);
        return ResponseEntity.status(201).body(ApiResponse.success(approvalRequestMapper.toShiftSwapResponse(saved)));
    }

    @GetMapping("/api/management/schedules/swaps/pending")
    public ResponseEntity<ApiResponse<List<ShiftSwapRequestResponse>>> getPendingSwaps() {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toShiftSwapResponses(approvalService.getPendingShiftSwaps())));
    }

    @PostMapping("/api/management/schedules/swaps/{id}/approve")
    public ResponseEntity<ApiResponse<ShiftSwapRequestResponse>> approveSwap(
            @PathVariable UUID id,
            @RequestParam ApprovalStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toShiftSwapResponse(approvalService.approveShiftSwap(id, status))));
    }

    @GetMapping("/api/schedules/swaps/my")
    public ResponseEntity<ApiResponse<List<ShiftSwapRequestResponse>>> getMySwaps() {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toShiftSwapResponses(approvalService.getMySwaps())));
    }

    @PostMapping("/api/schedules/swaps/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSwapRequest(@PathVariable UUID id) {
        approvalService.cancelShiftSwapRequest(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
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

    @PostMapping("/api/schedules/swaps")
    public ResponseEntity<ApiResponse<ShiftSwapRequest>> requestSwap(
            @RequestBody ShiftSwapRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(approvalService.requestShiftSwap(request)));
    }

    @GetMapping("/api/management/schedules/swaps/pending")
    public ResponseEntity<ApiResponse<List<ShiftSwapRequest>>> getPendingSwaps() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getPendingShiftSwaps()));
    }

    @PostMapping("/api/management/schedules/swaps/{id}/approve")
    public ResponseEntity<ApiResponse<ShiftSwapRequest>> approveSwap(
            @PathVariable UUID id,
            @RequestParam ApprovalStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.approveShiftSwap(id, status)));
    }

    @GetMapping("/api/schedules/swaps/my")
    public ResponseEntity<ApiResponse<List<ShiftSwapRequest>>> getMySwaps() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getMySwaps()));
    }
}

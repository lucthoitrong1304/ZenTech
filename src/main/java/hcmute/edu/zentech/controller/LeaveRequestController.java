package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.LeaveRequest;
import hcmute.edu.zentech.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LeaveRequestController {
    private final ApprovalService approvalService;

    @PostMapping("/api/leaves")
    public ResponseEntity<ApiResponse<LeaveRequest>> requestLeave(
            @RequestBody LeaveRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(approvalService.requestLeave(request)));
    }

    @GetMapping("/api/management/leaves/pending")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getPendingLeaves() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getPendingLeaves()));
    }

    @PostMapping("/api/management/leaves/{id}/approve")
    public ResponseEntity<ApiResponse<LeaveRequest>> approveLeave(
            @PathVariable UUID id,
            @RequestParam ApprovalStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.approveLeave(id, status)));
    }

    @GetMapping("/api/leaves/my")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> getMyLeaves() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getMyLeaves()));
    }
}

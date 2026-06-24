package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.LeaveRequestResponse;
import hcmute.edu.zentech.mapper.ApprovalRequestMapper;
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
    private final ApprovalRequestMapper approvalRequestMapper;

    @PostMapping("/api/leaves")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> requestLeave(
            @RequestBody LeaveRequest request
    ) {
        LeaveRequest saved = approvalService.requestLeave(request);
        return ResponseEntity.status(201).body(ApiResponse.success(approvalRequestMapper.toLeaveResponse(saved)));
    }

    @GetMapping("/api/management/leaves/pending")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getPendingLeaves() {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toLeaveResponses(approvalService.getPendingLeaves())));
    }

    @PostMapping("/api/management/leaves/{id}/approve")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approveLeave(
            @PathVariable UUID id,
            @RequestParam ApprovalStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toLeaveResponse(approvalService.approveLeave(id, status))));
    }

    @GetMapping("/api/leaves/my")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getMyLeaves() {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toLeaveResponses(approvalService.getMyLeaves())));
    }
}

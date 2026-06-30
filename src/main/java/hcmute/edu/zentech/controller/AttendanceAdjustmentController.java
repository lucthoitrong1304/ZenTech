package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.AttendanceAdjustmentResponse;
import hcmute.edu.zentech.mapper.ApprovalRequestMapper;
import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.AttendanceAdjustment;
import hcmute.edu.zentech.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AttendanceAdjustmentController {
    private final ApprovalService approvalService;
    private final ApprovalRequestMapper approvalRequestMapper;

    @PostMapping("/api/attendance/adjustments")
    public ResponseEntity<ApiResponse<AttendanceAdjustmentResponse>> requestAdjustment(
            @RequestBody AttendanceAdjustment request
    ) {
        AttendanceAdjustment saved = approvalService.requestAttendanceAdjustment(request);
        return ResponseEntity.status(201).body(ApiResponse.success(approvalRequestMapper.toAttendanceAdjustmentResponse(saved)));
    }

    @GetMapping("/api/management/attendance/adjustments/pending")
    public ResponseEntity<ApiResponse<List<AttendanceAdjustmentResponse>>> getPendingAdjustments() {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toAttendanceAdjustmentResponses(approvalService.getPendingAttendanceAdjustments())));
    }

    @PostMapping("/api/management/attendance/adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<AttendanceAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestParam ApprovalStatus status,
            @RequestParam(required = false) String rejectionReason
    ) {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toAttendanceAdjustmentResponse(approvalService.approveAttendanceAdjustment(id, status, rejectionReason))));
    }

    @GetMapping("/api/attendance/adjustments/my")
    public ResponseEntity<ApiResponse<List<AttendanceAdjustmentResponse>>> getMyAdjustments() {
        return ResponseEntity.ok(ApiResponse.success(approvalRequestMapper.toAttendanceAdjustmentResponses(approvalService.getMyAdjustments())));
    }

    @PostMapping("/api/attendance/adjustments/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelAdjustmentRequest(@PathVariable UUID id) {
        approvalService.cancelAttendanceAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

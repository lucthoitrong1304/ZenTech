package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
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

    @PostMapping("/api/attendance/adjustments")
    public ResponseEntity<ApiResponse<AttendanceAdjustment>> requestAdjustment(
            @RequestBody AttendanceAdjustment request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(approvalService.requestAttendanceAdjustment(request)));
    }

    @GetMapping("/api/management/attendance/adjustments/pending")
    public ResponseEntity<ApiResponse<List<AttendanceAdjustment>>> getPendingAdjustments() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getPendingAttendanceAdjustments()));
    }

    @PostMapping("/api/management/attendance/adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<AttendanceAdjustment>> approveAdjustment(
            @PathVariable UUID id,
            @RequestParam ApprovalStatus status,
            @RequestParam(required = false) String rejectionReason
    ) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.approveAttendanceAdjustment(id, status, rejectionReason)));
    }

    @GetMapping("/api/attendance/adjustments/my")
    public ResponseEntity<ApiResponse<List<AttendanceAdjustment>>> getMyAdjustments() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getMyAdjustments()));
    }
}

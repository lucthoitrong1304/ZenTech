package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.LeaveRequestCreateRequest;
import hcmute.edu.zentech.dto.request.LeaveTypeUpsertRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.EmployeeLeaveQuotaResponse;
import hcmute.edu.zentech.dto.response.LeaveRequestResponse;
import hcmute.edu.zentech.dto.response.LeaveTypeResponse;
import hcmute.edu.zentech.mapper.ApprovalRequestMapper;
import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.LeaveRequest;
import hcmute.edu.zentech.service.ApprovalService;
import hcmute.edu.zentech.service.LeaveManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LeaveRequestController {
    private final ApprovalService approvalService;
    private final LeaveManagementService leaveManagementService;
    private final ApprovalRequestMapper approvalRequestMapper;

    @PostMapping("/api/leaves")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> requestLeave(
            @Valid @RequestBody LeaveRequestCreateRequest request
    ) {
        LeaveRequest saved = approvalService.requestLeave(request);
        return ResponseEntity.status(201).body(ApiResponse.success(approvalRequestMapper.toLeaveResponse(saved)));
    }

    @GetMapping("/api/leave-types")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getActiveLeaveTypes() {
        return ResponseEntity.ok(ApiResponse.success(leaveManagementService.getActiveTypes()));
    }

    @GetMapping("/api/management/leave-types")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getManagedLeaveTypes() {
        return ResponseEntity.ok(ApiResponse.success(leaveManagementService.getAllTypes()));
    }

    @PostMapping("/api/management/leave-types")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> createLeaveType(
            @Valid @RequestBody LeaveTypeUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(leaveManagementService.createType(request)));
    }

    @PatchMapping("/api/management/leave-types/{id}")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> updateLeaveType(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveTypeUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(leaveManagementService.updateType(id, request)));
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

    @GetMapping("/api/leaves/my/quotas")
    public ResponseEntity<ApiResponse<List<EmployeeLeaveQuotaResponse>>> getMyLeaveQuotas(
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success(approvalService.getMyLeaveQuotas(targetYear)));
    }

    @PostMapping("/api/leaves/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelLeaveRequest(@PathVariable UUID id) {
        approvalService.cancelLeaveRequest(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

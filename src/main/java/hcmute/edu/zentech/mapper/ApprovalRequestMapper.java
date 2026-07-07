package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.ApprovalEmployeeResponse;
import hcmute.edu.zentech.dto.response.ApprovalShiftResponse;
import hcmute.edu.zentech.dto.response.AttendanceAdjustmentResponse;
import hcmute.edu.zentech.dto.response.LeaveRequestResponse;
import hcmute.edu.zentech.dto.response.LeaveTypeResponse;
import hcmute.edu.zentech.dto.response.ShiftSwapRequestResponse;
import hcmute.edu.zentech.model.AttendanceAdjustment;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.LeaveRequest;
import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.model.ShiftSwapRequest;
import hcmute.edu.zentech.service.LeaveManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalRequestMapper {
    private final LeaveManagementService leaveManagementService;

    public LeaveRequestResponse toLeaveResponse(LeaveRequest request) {
        if (request == null) {
            return null;
        }
        LeaveManagementService.LeaveQuotaImpact quotaImpact = leaveManagementService.calculateQuotaImpact(request);

        return LeaveRequestResponse.builder()
                .id(request.getId())
                .employee(toEmployeeResponse(request.getEmployee()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .leaveType(toLeaveTypeResponse(request.getLeaveType()))
                .amount(leaveManagementService.calculateAmount(request))
                .overQuota(quotaImpact.overQuota())
                .quotaRemainingBeforeRequest(quotaImpact.remainingBeforeRequest())
                .quotaRemainingAfterRequest(quotaImpact.remainingAfterRequest())
                .reason(request.getReason())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .targetShifts(request.getTargetShifts() == null ? null : request.getTargetShifts().stream().map(this::toShiftResponse).toList())
                .build();
    }

    public List<LeaveRequestResponse> toLeaveResponses(List<LeaveRequest> requests) {
        return requests.stream().map(this::toLeaveResponse).toList();
    }

    public ShiftSwapRequestResponse toShiftSwapResponse(ShiftSwapRequest request) {
        if (request == null) {
            return null;
        }

        return ShiftSwapRequestResponse.builder()
                .id(request.getId())
                .requester(toEmployeeResponse(request.getRequester()))
                .targetEmployee(toEmployeeResponse(request.getTargetEmployee()))
                .workDate(request.getWorkDate())
                .shift(toShiftResponse(request.getShift()))
                .targetWorkDate(request.getTargetWorkDate())
                .targetShift(toShiftResponse(request.getTargetShift()))
                .type(request.getType())
                .reason(request.getReason())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .build();
    }

    public List<ShiftSwapRequestResponse> toShiftSwapResponses(List<ShiftSwapRequest> requests) {
        return requests.stream().map(this::toShiftSwapResponse).toList();
    }

    public AttendanceAdjustmentResponse toAttendanceAdjustmentResponse(AttendanceAdjustment request) {
        if (request == null) {
            return null;
        }

        return AttendanceAdjustmentResponse.builder()
                .id(request.getId())
                .employee(toEmployeeResponse(request.getEmployee()))
                .workDate(request.getWorkDate())
                .type(request.getType())
                .proposedTime(request.getProposedTime())
                .reason(request.getReason())
                .status(request.getStatus())
                .rejectionReason(request.getRejectionReason())
                .requestedAt(request.getRequestedAt())
                .build();
    }

    public List<AttendanceAdjustmentResponse> toAttendanceAdjustmentResponses(List<AttendanceAdjustment> requests) {
        return requests.stream().map(this::toAttendanceAdjustmentResponse).toList();
    }

    private ApprovalEmployeeResponse toEmployeeResponse(Employee employee) {
        if (employee == null) {
            return null;
        }

        return ApprovalEmployeeResponse.builder()
                .id(employee.getId())
                .fullName(employee.getFullName())
                .build();
    }

    private LeaveTypeResponse toLeaveTypeResponse(hcmute.edu.zentech.model.LeaveType leaveType) {
        if (leaveType == null) {
            return null;
        }

        return LeaveTypeResponse.builder()
                .id(leaveType.getId())
                .code(leaveType.getCode())
                .name(leaveType.getName())
                .description(leaveType.getDescription())
                .unit(leaveType.getUnit())
                .active(leaveType.isActive())
                .systemDefault(leaveType.isSystemDefault())
                .sortOrder(leaveType.getSortOrder())
                .build();
    }

    private ApprovalShiftResponse toShiftResponse(Shift shift) {
        if (shift == null) {
            return null;
        }

        return ApprovalShiftResponse.builder()
                .id(shift.getId())
                .name(shift.getName())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .build();
    }
}

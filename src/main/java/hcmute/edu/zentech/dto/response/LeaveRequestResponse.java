package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequestResponse {
    private UUID id;
    private ApprovalEmployeeResponse employee;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LeaveTypeResponse leaveType;
    private BigDecimal amount;
    private String reason;
    private ApprovalStatus status;
    private LocalDateTime requestedAt;
    private List<ApprovalShiftResponse> targetShifts;
}

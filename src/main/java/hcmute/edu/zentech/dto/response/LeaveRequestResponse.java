package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private LeaveType leaveType;
    private String reason;
    private ApprovalStatus status;
    private LocalDateTime requestedAt;
}

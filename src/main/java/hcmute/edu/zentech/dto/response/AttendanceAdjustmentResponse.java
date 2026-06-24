package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.AdjustmentType;
import hcmute.edu.zentech.model.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAdjustmentResponse {
    private UUID id;
    private ApprovalEmployeeResponse employee;
    private LocalDate workDate;
    private AdjustmentType type;
    private LocalTime proposedTime;
    private String reason;
    private ApprovalStatus status;
    private String rejectionReason;
    private LocalDateTime requestedAt;
}

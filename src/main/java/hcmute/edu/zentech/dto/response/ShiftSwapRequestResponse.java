package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.SwapRequestType;
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
public class ShiftSwapRequestResponse {
    private UUID id;
    private ApprovalEmployeeResponse requester;
    private ApprovalEmployeeResponse targetEmployee;
    private LocalDate workDate;
    private ApprovalShiftResponse shift;
    private LocalDate targetWorkDate;
    private ApprovalShiftResponse targetShift;
    private SwapRequestType type;
    private String reason;
    private ApprovalStatus status;
    private LocalDateTime requestedAt;
}

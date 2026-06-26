package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeLeaveQuotaResponse {
    private UUID employeeId;
    private UUID leaveTypeId;
    private LeaveTypeResponse leaveType;
    private int year;
    private BigDecimal entitlement;
    private BigDecimal approvedUsed;
    private BigDecimal pendingUsed;
    private BigDecimal remaining;
}

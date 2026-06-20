package hcmute.edu.zentech.dto.shift;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class EmployeeShiftDto {
    @NotNull
    private UUID employeeId;
    
    @NotNull
    private UUID shiftId;
    
    @NotNull
    private LocalDate workDate;

    private String reason;
}


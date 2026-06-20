package hcmute.edu.zentech.dto.shift;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class BulkShiftUpdateDto {
    private List<UUID> employeeIds;
    private boolean selectAll;
    
    @NotNull
    private UUID shiftId;
    
    @NotNull
    private LocalDate startDate;
    
    @NotNull
    private LocalDate endDate;

    private String reason;
}


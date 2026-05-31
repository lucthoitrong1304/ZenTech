package hcmute.edu.zentech.dto.shift;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CopyWeekDto {
    @NotNull
    private LocalDate fromWeekStartDate;
    
    @NotNull
    private LocalDate fromWeekEndDate;
    
    @NotNull
    private LocalDate toWeekStartDate;
    
    @NotNull
    private LocalDate toWeekEndDate;
}

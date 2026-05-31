package hcmute.edu.zentech.dto.shift;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleResponse {
    private Page<EmployeeWeeklyScheduleDto> employees;
}

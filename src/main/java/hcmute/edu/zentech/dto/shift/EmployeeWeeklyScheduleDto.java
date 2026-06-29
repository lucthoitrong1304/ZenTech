package hcmute.edu.zentech.dto.shift;

import hcmute.edu.zentech.model.ShiftType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class EmployeeWeeklyScheduleDto {
    private UUID employeeId;
    private String employeeName;
    private List<DailyShiftDto> shifts;

    @Data
    public static class DailyShiftDto {
        private UUID employeeShiftId;
        private UUID shiftId;
        private String shiftName;
        private String colorCode;
        private LocalDate workDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private ShiftType shiftType;
        private Integer earlyCheckInMinutes;
        private Integer lateCheckOutMinutes;
        private Integer onTimeCheckInStartMinutes;
        private Integer onTimeCheckInEndMinutes;
        private Integer onTimeCheckOutStartMinutes;
        private Integer onTimeCheckOutEndMinutes;
    }
}

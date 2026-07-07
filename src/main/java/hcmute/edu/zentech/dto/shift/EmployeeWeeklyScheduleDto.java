package hcmute.edu.zentech.dto.shift;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime startTime;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime endTime;
        private ShiftType shiftType;
        private Integer earlyCheckInMinutes;
        private Integer lateCheckOutMinutes;
        private Integer onTimeCheckInStartMinutes;
        private Integer onTimeCheckInEndMinutes;
        private Integer onTimeCheckOutStartMinutes;
        private Integer onTimeCheckOutEndMinutes;
        @JsonProperty("isLeave")
        private boolean isLeave;
        @JsonProperty("isWfh")
        private boolean isWfh;
        @JsonProperty("isAfk")
        private boolean isAfk;
        @JsonProperty("isSwap")
        private boolean isSwap;
        private String statusLabel;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime afkStartTime;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime afkEndTime;
        private String originalShiftName;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime originalStartTime;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        private LocalTime originalEndTime;
        private String changeDescription;
    }
}

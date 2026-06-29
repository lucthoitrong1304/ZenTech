package hcmute.edu.zentech.dto.shift;

import hcmute.edu.zentech.model.ShiftType;
import lombok.Data;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class ShiftDto {
    private UUID id;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private String colorCode;
    private boolean isDefault;
    private ShiftType type;
    private Integer earlyCheckInMinutes;
    private Integer lateCheckOutMinutes;
    private Integer onTimeCheckInStartMinutes;
    private Integer onTimeCheckInEndMinutes;
    private Integer onTimeCheckOutStartMinutes;
    private Integer onTimeCheckOutEndMinutes;
}

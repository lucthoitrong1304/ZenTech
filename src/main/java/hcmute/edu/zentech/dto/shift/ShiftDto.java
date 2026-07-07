package hcmute.edu.zentech.dto.shift;

import com.fasterxml.jackson.annotation.JsonFormat;
import hcmute.edu.zentech.model.ShiftType;
import lombok.Data;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class ShiftDto {
    private UUID id;
    private String name;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime startTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
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

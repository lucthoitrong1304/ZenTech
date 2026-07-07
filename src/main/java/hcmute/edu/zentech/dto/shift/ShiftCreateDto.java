package hcmute.edu.zentech.dto.shift;

import com.fasterxml.jackson.annotation.JsonFormat;
import hcmute.edu.zentech.model.ShiftType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class ShiftCreateDto {
    @NotBlank(message = "Tên ca không được để trống")
    private String name;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String colorCode;

    private boolean isDefault;

    @NotNull(message = "Loại ca không được để trống")
    private ShiftType type;

    private Integer earlyCheckInMinutes;

    private Integer lateCheckOutMinutes;

    private Integer onTimeCheckInStartMinutes;

    private Integer onTimeCheckInEndMinutes;

    private Integer onTimeCheckOutStartMinutes;

    private Integer onTimeCheckOutEndMinutes;
}

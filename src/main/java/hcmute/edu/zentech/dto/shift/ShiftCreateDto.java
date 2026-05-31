package hcmute.edu.zentech.dto.shift;

import hcmute.edu.zentech.model.ShiftType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class ShiftCreateDto {
    @NotBlank(message = "Tên ca không được để trống")
    private String name;

    private LocalTime startTime;

    private LocalTime endTime;

    private String colorCode;

    private boolean isDefault;

    @NotNull(message = "Loại ca không được để trống")
    private ShiftType type;
}

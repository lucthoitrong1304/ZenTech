package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.shift.ShiftDto;
import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.model.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftMapperTest {
    private final ShiftMapper shiftMapper = new ShiftMapper();

    @Test
    void toDtoPreservesLocalShiftTimes() {
        Shift shift = new Shift();
        shift.setName("AM");
        shift.setType(ShiftType.NORMAL);
        shift.setStartTime(LocalTime.of(9, 0));
        shift.setEndTime(LocalTime.of(12, 0));

        ShiftDto dto = shiftMapper.toDto(shift);

        assertEquals(LocalTime.of(9, 0), dto.getStartTime());
        assertEquals(LocalTime.of(12, 0), dto.getEndTime());
    }
}

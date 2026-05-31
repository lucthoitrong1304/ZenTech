package hcmute.edu.zentech.repository.projection;

import hcmute.edu.zentech.model.ShiftType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface EmployeeWeeklyScheduleProjection {
    UUID getEmployeeId();
    UUID getShiftId();
    String getShiftName();
    String getColorCode();
    LocalDate getWorkDate();
    LocalTime getStartTime();
    LocalTime getEndTime();
    ShiftType getShiftType();
}

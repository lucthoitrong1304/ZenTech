package hcmute.edu.zentech.repository.projection;

import hcmute.edu.zentech.model.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AttendanceRecordProjection {
    UUID getId();
    UUID getEmployeeId();
    String getEmployeeName();
    LocalDateTime getCheckInTime();
    AttendanceStatus getStatus();
}

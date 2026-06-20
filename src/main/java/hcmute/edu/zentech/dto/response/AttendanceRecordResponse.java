package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecordResponse {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private LocalDate workDate;
    private String shiftName;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private double workingHours;
    private long lateMinutes;
    private long earlyMinutes;
    private String status; // e.g. ON_TIME, LATE, EARLY, ABSENT_UNEXCUSED, ABSENT_EXCUSED, MISSING_CHECK_IN, MISSING_CHECK_OUT, OFF
    private List<LocalDateTime> detailTimes;
}


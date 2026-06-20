package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatisticsResponse {
    private long totalRecords;
    private long totalOnTime;
    private long totalLate;
    private long totalEarly;
    private double totalWorkingHours;
    private long totalMissingCheckIn;
    private long totalMissingCheckOut;
    private long totalAbsent;
    private long totalLeave;
}


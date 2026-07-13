package hcmute.edu.zentech.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceDaySummaryResponse {
    private long totalEmployees, totalShifts, onTime, earlyArrival, late, earlyCheckout;
    private long leave, workFromHome, absent, missingCheckIn, missingCheckOut, notStarted, provisional;
    private double totalWorkingHours;
}

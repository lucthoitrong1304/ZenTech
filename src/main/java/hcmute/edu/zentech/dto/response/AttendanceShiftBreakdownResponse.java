package hcmute.edu.zentech.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceShiftBreakdownResponse {
    private UUID shiftId;
    private UUID employeeShiftId;
    private String shiftName;
    private LocalTime scheduledStartTime;
    private LocalTime scheduledEndTime;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private double workingHours;
    private long lateMinutes;
    private long earlyMinutes;
    private String status;
    @JsonProperty("isProvisional")
    private boolean isProvisional;
    @JsonProperty("isLeave")
    private boolean isLeave;
    @JsonProperty("isWfh")
    private boolean isWfh;
    @JsonProperty("isAfk")
    private boolean isAfk;
    private double afkHours;
    @JsonProperty("isSwap")
    private boolean isSwap;
    private String originalShiftName;
    private String changeDescription;
    private List<AttendanceEventTimelineResponse> events;
}

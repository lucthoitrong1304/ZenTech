package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecordResponse {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private LocalDateTime checkInTime;
    private AttendanceStatus status;
}

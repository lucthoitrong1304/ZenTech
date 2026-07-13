package hcmute.edu.zentech.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceDayGroupResponse {
    private LocalDate workDate;
    private AttendanceDaySummaryResponse summary;
    private List<AttendanceRecordResponse> records;
}

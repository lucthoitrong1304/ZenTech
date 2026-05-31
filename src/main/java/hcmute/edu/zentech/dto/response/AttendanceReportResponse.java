package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceReportResponse {
    private AttendanceStatisticsResponse statistics;
    private PageResponse<AttendanceRecordResponse> records;
}

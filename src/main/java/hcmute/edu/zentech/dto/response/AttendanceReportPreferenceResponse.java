package hcmute.edu.zentech.dto.response;
import lombok.*; import java.time.LocalDateTime; import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceReportPreferenceResponse { private List<String> visibleMetrics; private LocalDateTime updatedAt; }

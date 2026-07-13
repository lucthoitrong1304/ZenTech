package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "attendance_report_preferences") @Data @NoArgsConstructor @AllArgsConstructor
public class AttendanceReportPreference {
    @Id private UUID accountId;
    @Column(nullable = false, length = 1000) private String visibleMetrics;
    @Column(nullable = false) private LocalDateTime updatedAt;
}

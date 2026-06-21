package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "trace_id")
    private String traceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "account_id")
    private AccountUser user;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "api_path")
    private String apiPath;

    @Column(name = "http_method")
    private String httpMethod;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    /**
     * Thời điểm sự cố xảy ra LẦN ĐẦU TIÊN.
     * Không bao giờ bị ghi đè khi có occurrence mới.
     * Dùng làm mốc bắt đầu cho cửa sổ tính toán business impact.
     */
    @Column(name = "first_occurred_at")
    private Instant firstOccurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private String assignee;

    @Column(name = "images", length = 2000)
    private String images;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        // firstOccurredAt chỉ set một lần khi tạo mới
        if (firstOccurredAt == null) {
            firstOccurredAt = occurredAt;
        }
        if (status == null) {
            status = IncidentStatus.OPEN;
        }
        if (severity == null) {
            severity = IncidentSeverity.LOW;
        }
    }
}

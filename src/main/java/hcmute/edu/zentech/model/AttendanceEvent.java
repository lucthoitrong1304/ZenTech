package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_events")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AttendanceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_shift_id")
    private EmployeeShift employeeShift;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceEventType eventType;

    private String source;

    private String details;

    private Double latitude;

    private Double longitude;

    private Double accuracyMeters;

    private Boolean locationValid;

    private String faceImageKey;
}

package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_location_policies")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AttendanceLocationPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private boolean enabled;

    @Enumerated(EnumType.STRING)
    private AttendanceLocationShapeType shapeType;

    private Double centerLatitude;

    private Double centerLongitude;

    private Double radiusMeters;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String polygonPointsJson;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AccountUser updatedBy;
}

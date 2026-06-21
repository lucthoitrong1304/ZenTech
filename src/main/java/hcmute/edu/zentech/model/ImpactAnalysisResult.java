package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "impact_analysis_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactAnalysisResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @Column(name = "actual_revenue")
    private Double actualRevenue;

    @Column(name = "expected_revenue")
    private Double expectedRevenue;

    @Column(name = "revenue_loss")
    private Double revenueLoss;

    @Column(name = "actual_orders")
    private Integer actualOrders;

    @Column(name = "expected_orders")
    private Integer expectedOrders;

    @Column(name = "lost_orders")
    private Integer lostOrders;

    @Column(name = "affected_users")
    private Integer affectedUsers;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private IncidentSeverity severity;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

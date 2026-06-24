package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "return_requests")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ReturnRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "return_request_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String reason;

    @Column(length = 1000)
    private String details;

    @Column(length = 2000)
    private String proofFileKeys; // comma-separated keys

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnRequestStatus status; // PENDING, APPROVED, REJECTED

    private boolean resellable;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}

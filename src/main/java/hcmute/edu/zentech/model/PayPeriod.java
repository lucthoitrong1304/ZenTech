package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pay_periods")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PayPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private boolean isLocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_id")
    private AccountUser lockedBy;

    private LocalDateTime lockedAt;
}

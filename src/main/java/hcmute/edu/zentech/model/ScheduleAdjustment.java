package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_adjustments")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ScheduleAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_shift_id")
    private Shift originalShift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_shift_id")
    private Shift adjustedShift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_by_id", nullable = false)
    private AccountUser adjustedBy;

    @Column(nullable = false)
    private LocalDateTime adjustedAt;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;
}

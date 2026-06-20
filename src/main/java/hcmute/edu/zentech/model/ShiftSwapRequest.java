package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shift_swap_requests")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ShiftSwapRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Employee requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_employee_id", nullable = false)
    private Employee targetEmployee;

    @Column(nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    private LocalDate targetWorkDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_shift_id")
    private Shift targetShift;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SwapRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private AccountUser approvedBy;

    private LocalDateTime approvedAt;
}

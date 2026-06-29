package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id")
    private LeaveType leaveType;

    private java.time.LocalTime startTime;

    private java.time.LocalTime endTime;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private AccountUser approvedBy;

    private LocalDateTime approvedAt;

    @ManyToMany
    @JoinTable(
        name = "leave_request_shifts",
        joinColumns = @JoinColumn(name = "leave_request_id"),
        inverseJoinColumns = @JoinColumn(name = "shift_id")
    )
    private List<Shift> targetShifts = new ArrayList<>();
}

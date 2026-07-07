package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.AttendanceEvent;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.EmployeeShift;
import hcmute.edu.zentech.model.PayPeriod;
import hcmute.edu.zentech.model.ScheduleAdjustment;
import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.model.ShiftType;
import hcmute.edu.zentech.repository.AttendanceEventRepository;
import hcmute.edu.zentech.repository.PayPeriodRepository;
import hcmute.edu.zentech.repository.ScheduleAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ScheduleMutationPolicy {
    private final PayPeriodRepository payPeriodRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final ScheduleAdjustmentRepository scheduleAdjustmentRepository;

    public void assertPayPeriodUnlocked(LocalDate workDate) {
        Optional<PayPeriod> periodOpt = payPeriodRepository.findPeriodActiveAt(workDate);
        if (periodOpt.isPresent() && periodOpt.get().isLocked()) {
            throw new RuntimeException("Kỳ công chứa ngày " + workDate + " đã bị khóa. Không thể thay đổi lịch.");
        }
    }

    public boolean requiresAdjustment(Employee employee, LocalDate workDate) {
        LocalDate today = LocalDate.now();
        if (workDate.isBefore(today)) {
            return true;
        }
        if (hasApprovedScheduleAdjustment(employee.getId(), workDate)) {
            return true;
        }
        return workDate.equals(today) && hasAttendanceEvents(employee.getId(), workDate);
    }

    public boolean shouldDeleteByAdjustment(EmployeeShift assignment) {
        LocalDate workDate = assignment.getWorkDate();
        LocalDate today = LocalDate.now();
        if (workDate.isBefore(today)) {
            return true;
        }
        if (hasApprovedScheduleAdjustment(assignment.getEmployee().getId(), workDate)) {
            return true;
        }
        if (!workDate.equals(today)) {
            return false;
        }
        if (hasStartedToday(assignment.getWorkDate(), assignment.getShift())) {
            return true;
        }
        return hasAttendanceEvents(assignment.getEmployee().getId(), workDate)
                || hasAttendanceEventsForAssignment(assignment.getEmployee().getId(), assignment.getId(), workDate);
    }

    public void assertTodayShiftStillUseful(LocalDate workDate, Shift shift) {
        if (!workDate.equals(LocalDate.now()) || !hasScheduledWindow(shift)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledStart = LocalDateTime.of(workDate, shift.getStartTime());
        LocalDateTime scheduledEnd = resolveScheduledEnd(workDate, shift);
        if (!now.isBefore(scheduledEnd)) {
            throw new RuntimeException("Giờ làm việc của ca " + shift.getName() + " đã qua, bạn không thể gắn trực tiếp cho hôm nay. Vui lòng thực hiện qua luồng điều chỉnh/duyệt.");
        }
        if (!now.isBefore(scheduledStart)) {
            throw new RuntimeException("Ca " + shift.getName() + " đã bắt đầu, không thể gắn trực tiếp cho hôm nay. Vui lòng thực hiện qua luồng điều chỉnh/duyệt.");
        }
    }

    public List<AttendanceEvent> findAttendanceEvents(UUID employeeId, LocalDate workDate) {
        return attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(
                employeeId,
                workDate.atStartOfDay(),
                workDate.atTime(LocalTime.MAX)
        );
    }

    public List<ScheduleAdjustment> findApprovedScheduleAdjustments(UUID employeeId, LocalDate workDate) {
        return scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(employeeId, workDate, workDate).stream()
                .filter(adjustment -> adjustment.getStatus() == ApprovalStatus.APPROVED)
                .toList();
    }

    private boolean hasAttendanceEvents(UUID employeeId, LocalDate workDate) {
        return !findAttendanceEvents(employeeId, workDate).isEmpty();
    }

    private boolean hasAttendanceEventsForAssignment(UUID employeeId, UUID assignmentId, LocalDate workDate) {
        return findAttendanceEvents(employeeId, workDate).stream()
                .anyMatch(event -> event.getEmployeeShift() != null && assignmentId.equals(event.getEmployeeShift().getId()));
    }

    private boolean hasApprovedScheduleAdjustment(UUID employeeId, LocalDate workDate) {
        return !findApprovedScheduleAdjustments(employeeId, workDate).isEmpty();
    }

    private boolean hasStartedToday(LocalDate workDate, Shift shift) {
        return workDate.equals(LocalDate.now())
                && hasScheduledWindow(shift)
                && !LocalDateTime.now().isBefore(LocalDateTime.of(workDate, shift.getStartTime()));
    }

    private LocalDateTime resolveScheduledEnd(LocalDate workDate, Shift shift) {
        LocalDateTime scheduledEnd = LocalDateTime.of(workDate, shift.getEndTime());
        if (!shift.getEndTime().isAfter(shift.getStartTime())) {
            scheduledEnd = scheduledEnd.plusDays(1);
        }
        return scheduledEnd;
    }

    private boolean hasScheduledWindow(Shift shift) {
        return shift != null
                && shift.getType() != ShiftType.OFF
                && shift.getStartTime() != null
                && shift.getEndTime() != null;
    }
}

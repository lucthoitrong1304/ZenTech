package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.EmployeeShift;
import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.model.ShiftType;
import hcmute.edu.zentech.repository.AttendanceEventRepository;
import hcmute.edu.zentech.repository.PayPeriodRepository;
import hcmute.edu.zentech.repository.ScheduleAdjustmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleMutationPolicyTest {
    @Mock
    private PayPeriodRepository payPeriodRepository;
    @Mock
    private AttendanceEventRepository attendanceEventRepository;
    @Mock
    private ScheduleAdjustmentRepository scheduleAdjustmentRepository;

    @Test
    void assertTodayShiftStillUseful_blocksWhenShiftAlreadyStarted() {
        ScheduleMutationPolicy policy = new ScheduleMutationPolicy(
                payPeriodRepository,
                attendanceEventRepository,
                scheduleAdjustmentRepository
        );
        Shift allDayShift = shift("Ca dang dien ra", LocalTime.MIDNIGHT, LocalTime.of(23, 59));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> policy.assertTodayShiftStillUseful(LocalDate.now(), allDayShift));

        assertTrue(ex.getMessage().contains("đã bắt đầu"));
    }

    @Test
    void assertTodayShiftStillUseful_usesExpiredMessageWhenShiftAlreadyEnded() {
        ScheduleMutationPolicy policy = new ScheduleMutationPolicy(
                payPeriodRepository,
                attendanceEventRepository,
                scheduleAdjustmentRepository
        );
        Shift endedShift = shift("Ca da qua", LocalTime.MIDNIGHT, LocalTime.of(0, 1));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> policy.assertTodayShiftStillUseful(LocalDate.now(), endedShift));

        assertTrue(ex.getMessage().contains("đã qua"));
    }

    @Test
    void assertTodayShiftStillUseful_ignoresOffShift() {
        ScheduleMutationPolicy policy = new ScheduleMutationPolicy(
                payPeriodRepository,
                attendanceEventRepository,
                scheduleAdjustmentRepository
        );
        Shift offShift = new Shift();
        offShift.setId(UUID.randomUUID());
        offShift.setName("Nghi");
        offShift.setType(ShiftType.OFF);

        assertDoesNotThrow(() -> policy.assertTodayShiftStillUseful(LocalDate.now(), offShift));
    }

    @Test
    void shouldDeleteByAdjustment_returnsTrueWhenTodayShiftAlreadyStarted() {
        ScheduleMutationPolicy policy = new ScheduleMutationPolicy(
                payPeriodRepository,
                attendanceEventRepository,
                scheduleAdjustmentRepository
        );
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        LocalDate today = LocalDate.now();
        Shift allDayShift = shift("Ca dang dien ra", LocalTime.MIDNIGHT, LocalTime.of(23, 59));
        EmployeeShift assignment = new EmployeeShift();
        assignment.setId(UUID.randomUUID());
        assignment.setEmployee(employee);
        assignment.setShift(allDayShift);
        assignment.setWorkDate(today);
        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(
                employee.getId(),
                today,
                today
        )).thenReturn(java.util.List.of());

        assertTrue(policy.shouldDeleteByAdjustment(assignment));
    }

    private Shift shift(String name, LocalTime startTime, LocalTime endTime) {
        Shift shift = new Shift();
        shift.setId(UUID.randomUUID());
        shift.setName(name);
        shift.setType(ShiftType.NORMAL);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        return shift;
    }
}

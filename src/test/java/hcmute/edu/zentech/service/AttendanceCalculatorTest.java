package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AttendanceRecordResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceCalculatorTest {

    @Mock
    private EmployeeShiftRepository employeeShiftRepository;
    @Mock
    private ScheduleAdjustmentRepository scheduleAdjustmentRepository;
    @Mock
    private ShiftSwapRequestRepository shiftSwapRequestRepository;
    @Mock
    private AttendanceEventRepository attendanceEventRepository;
    @Mock
    private AttendanceAdjustmentRepository attendanceAdjustmentRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    private AttendanceCalculator attendanceCalculator;

    private Employee employee;
    private Shift baseShift;
    private LocalDate workDate;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFullName("Nguyễn Văn A");

        baseShift = new Shift();
        baseShift.setId(UUID.randomUUID());
        baseShift.setName("Ca Sáng");
        baseShift.setStartTime(LocalTime.of(8, 0));
        baseShift.setEndTime(LocalTime.of(12, 0));
        baseShift.setGracePeriodMinutes(15);
        baseShift.setType(ShiftType.NORMAL);

        workDate = LocalDate.of(2026, 6, 20);
    }

    @Test
    void testResolveEffectiveShift_BaseShiftOnly() {
        // Arrange
        EmployeeShift es = new EmployeeShift();
        es.setShift(baseShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(es));

        // Act
        Shift resolved = attendanceCalculator.resolveEffectiveShift(employee.getId(), workDate);

        // Assert
        assertNotNull(resolved);
        assertEquals(baseShift.getId(), resolved.getId());
    }

    @Test
    void testResolveEffectiveShift_ScheduleAdjustmentApproved() {
        // Arrange
        Shift adjustedShift = new Shift();
        adjustedShift.setId(UUID.randomUUID());
        adjustedShift.setName("Ca Chiều");

        ScheduleAdjustment adjustment = new ScheduleAdjustment();
        adjustment.setStatus(ApprovalStatus.APPROVED);
        adjustment.setAdjustedShift(adjustedShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(employee.getId(), workDate, workDate))
                .thenReturn(List.of(adjustment));

        // Act
        Shift resolved = attendanceCalculator.resolveEffectiveShift(employee.getId(), workDate);

        // Assert
        assertNotNull(resolved);
        assertEquals(adjustedShift.getId(), resolved.getId());
        verify(employeeShiftRepository, never()).findByEmployeeIdAndWorkDate(any(), any());
    }

    @Test
    void resolveEffectiveShifts_marksAfkOnlyOnOverlappingShift() {
        Shift nightShift = new Shift();
        nightShift.setId(UUID.randomUUID());
        nightShift.setName("Night");
        nightShift.setStartTime(LocalTime.of(18, 0));
        nightShift.setEndTime(LocalTime.of(22, 0));
        nightShift.setType(ShiftType.NORMAL);

        EmployeeShift amAssignment = new EmployeeShift();
        amAssignment.setShift(baseShift);
        EmployeeShift nightAssignment = new EmployeeShift();
        nightAssignment.setShift(nightShift);

        LeaveType afkType = LeaveType.builder()
                .id(UUID.randomUUID())
                .code("AFK")
                .name("AFK")
                .unit(LeaveTypeUnit.HOUR)
                .active(true)
                .systemDefault(true)
                .sortOrder(30)
                .build();
        LeaveRequest afk = new LeaveRequest();
        afk.setLeaveType(afkType);
        afk.setStartTime(LocalTime.of(9, 0));
        afk.setEndTime(LocalTime.of(10, 0));

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(amAssignment, nightAssignment));
        when(leaveRequestRepository.findLeavesForEmployeeInRangeWithStatuses(eq(employee.getId()), eq(workDate), eq(workDate), anyList()))
                .thenReturn(List.of(afk));

        List<AttendanceCalculator.EffectiveShift> shifts = attendanceCalculator.resolveEffectiveShifts(employee.getId(), workDate);

        assertEquals(2, shifts.size());
        assertTrue(shifts.get(0).isAfk());
        assertFalse(shifts.get(1).isAfk());
    }

    @Test
    void testCalculateDayAttendance_OnTime() {
        // Arrange
        EmployeeShift es = new EmployeeShift();
        es.setShift(baseShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(es));

        // 8:00 - 12:00
        AttendanceEvent in = new AttendanceEvent();
        in.setEventType(AttendanceEventType.CHECK_IN);
        in.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(8, 0)));

        AttendanceEvent out = new AttendanceEvent();
        out.setEventType(AttendanceEventType.CHECK_OUT);
        out.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(12, 0)));

        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(List.of(in, out));
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        // Assert
        assertNotNull(response);
        assertEquals("ON_TIME", response.getStatus());
        assertEquals(4.0, response.getWorkingHours());
        assertEquals(0, response.getLateMinutes());
        assertEquals(0, response.getEarlyMinutes());
    }

    @Test
    void testCalculateDayAttendance_LateAndEarly() {
        // Arrange
        EmployeeShift es = new EmployeeShift();
        es.setShift(baseShift); // 8:00 - 12:00, grace 15 mins

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(es));

        // Check in at 8:20 (Late, > 15 mins grace), check out at 11:50 (Early Checkout)
        AttendanceEvent in = new AttendanceEvent();
        in.setEventType(AttendanceEventType.CHECK_IN);
        in.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(8, 20)));

        AttendanceEvent out = new AttendanceEvent();
        out.setEventType(AttendanceEventType.CHECK_OUT);
        out.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(11, 50)));

        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(List.of(in, out));
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        // Assert
        assertNotNull(response);
        assertEquals("LATE_AND_EARLY", response.getStatus());
        assertEquals(15, response.getLateMinutes()); // 8:20 - allowed on-time until 8:05
        assertEquals(5, response.getEarlyMinutes()); // on-time checkout starts at 11:55
        assertEquals(3.5, response.getWorkingHours()); // 3 hours 30 mins
    }

    @Test
    void testCalculateDayAttendance_LeaveRequestApproved() {
        // Arrange
        EmployeeShift es = new EmployeeShift();
        es.setShift(baseShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(es));

        // No events
        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Approved Leave Request
        LeaveRequest leave = new LeaveRequest();
        leave.setStatus(ApprovalStatus.APPROVED);
        LeaveType leaveType = LeaveType.builder()
                .id(UUID.randomUUID())
                .code("NGHI")
                .name("Nghỉ")
                .unit(LeaveTypeUnit.DAY)
                .active(true)
                .systemDefault(true)
                .sortOrder(10)
                .build();
        leave.setLeaveType(leaveType);
        when(leaveRequestRepository.findApprovedLeavesForEmployeeInRange(employee.getId(), workDate, workDate, ApprovalStatus.APPROVED))
                .thenReturn(List.of(leave));

        // Act
        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        // Assert
        assertNotNull(response);
        assertEquals("ABSENT_EXCUSED", response.getStatus());
        assertEquals(0.0, response.getWorkingHours());
    }

    @Test
    void testCalculateDayAttendance_AbsentUnexcused() {
        // Arrange
        EmployeeShift es = new EmployeeShift();
        es.setShift(baseShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(es));

        // No events
        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // No leaves approved
        when(leaveRequestRepository.findApprovedLeavesForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        // Assert
        assertNotNull(response);
        assertEquals("ABSENT_UNEXCUSED", response.getStatus());
    }

    @Test
    void testCalculateDayAttendance_MissingCheckOut() {
        // Arrange
        EmployeeShift es = new EmployeeShift();
        es.setShift(baseShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(es));

        // Only check in event
        AttendanceEvent in = new AttendanceEvent();
        in.setEventType(AttendanceEventType.CHECK_IN);
        in.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(8, 0)));

        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(List.of(in));
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        // Assert
        assertNotNull(response);
        assertEquals("MISSING_CHECK_OUT", response.getStatus());
        assertEquals(0.0, response.getWorkingHours());
    }

    @Test
    void testCalculateDayAttendance_MultipleShiftsBreakdown() {
        Shift pmShift = new Shift();
        pmShift.setId(UUID.randomUUID());
        pmShift.setName("Ca Chiá»u");
        pmShift.setStartTime(LocalTime.of(13, 0));
        pmShift.setEndTime(LocalTime.of(17, 0));
        pmShift.setType(ShiftType.NORMAL);

        EmployeeShift amAssignment = new EmployeeShift();
        amAssignment.setId(UUID.randomUUID());
        amAssignment.setShift(baseShift);

        EmployeeShift pmAssignment = new EmployeeShift();
        pmAssignment.setId(UUID.randomUUID());
        pmAssignment.setShift(pmShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(amAssignment, pmAssignment));

        AttendanceEvent amIn = new AttendanceEvent();
        amIn.setEmployeeShift(amAssignment);
        amIn.setEventType(AttendanceEventType.CHECK_IN);
        amIn.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(8, 0)));

        AttendanceEvent amOut = new AttendanceEvent();
        amOut.setEmployeeShift(amAssignment);
        amOut.setEventType(AttendanceEventType.CHECK_OUT);
        amOut.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(12, 0)));

        AttendanceEvent pmIn = new AttendanceEvent();
        pmIn.setEmployeeShift(pmAssignment);
        pmIn.setEventType(AttendanceEventType.CHECK_IN);
        pmIn.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(13, 0)));

        AttendanceEvent pmOut = new AttendanceEvent();
        pmOut.setEmployeeShift(pmAssignment);
        pmOut.setEventType(AttendanceEventType.CHECK_OUT);
        pmOut.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(17, 0)));

        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(List.of(amIn, amOut, pmIn, pmOut));
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedLeavesForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        assertEquals("ON_TIME", response.getStatus());
        assertEquals(8.0, response.getWorkingHours());
        assertEquals(2, response.getShiftBreakdowns().size());
        assertEquals(4.0, response.getShiftBreakdowns().get(0).getWorkingHours());
        assertEquals(4.0, response.getShiftBreakdowns().get(1).getWorkingHours());
        assertFalse(response.isProvisional());
    }

    @Test
    void testCalculateDayAttendance_CurrentDayMissingCheckoutIsProvisional() {
        LocalDate today = LocalDate.now();
        LocalDateTime checkInTime = LocalDateTime.now().minusMinutes(75);

        Shift currentShift = new Shift();
        currentShift.setId(UUID.randomUUID());
        currentShift.setName("Ca Hiá»‡n Táº¡i");
        currentShift.setStartTime(checkInTime.toLocalTime().minusMinutes(5));
        currentShift.setEndTime(LocalTime.now().plusHours(2));
        currentShift.setType(ShiftType.NORMAL);

        EmployeeShift assignment = new EmployeeShift();
        assignment.setId(UUID.randomUUID());
        assignment.setShift(currentShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), today))
                .thenReturn(List.of(assignment));

        AttendanceEvent in = new AttendanceEvent();
        in.setEmployeeShift(assignment);
        in.setEventType(AttendanceEventType.CHECK_IN);
        in.setTimestamp(checkInTime);

        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(List.of(in));
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedLeavesForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, today);

        assertEquals("MISSING_CHECK_OUT", response.getStatus());
        assertTrue(response.isProvisional());
        assertTrue(response.getWorkingHours() >= 1.0);
        assertEquals(1, response.getShiftBreakdowns().size());
        assertTrue(response.getShiftBreakdowns().get(0).isProvisional());
    }

    @Test
    void testCalculateDayAttendance_PartialWorkedDayDoesNotShowAbsentAsDayStatus() {
        Shift pmShift = new Shift();
        pmShift.setId(UUID.randomUUID());
        pmShift.setName("Ca Chiá»u");
        pmShift.setStartTime(LocalTime.of(13, 0));
        pmShift.setEndTime(LocalTime.of(17, 0));
        pmShift.setType(ShiftType.NORMAL);

        EmployeeShift amAssignment = new EmployeeShift();
        amAssignment.setId(UUID.randomUUID());
        amAssignment.setShift(baseShift);

        EmployeeShift pmAssignment = new EmployeeShift();
        pmAssignment.setId(UUID.randomUUID());
        pmAssignment.setShift(pmShift);

        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shiftSwapRequestRepository.findApprovedSwapsForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(amAssignment, pmAssignment));

        AttendanceEvent amIn = new AttendanceEvent();
        amIn.setEmployeeShift(amAssignment);
        amIn.setEventType(AttendanceEventType.CHECK_IN);
        amIn.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(8, 20)));

        AttendanceEvent amOut = new AttendanceEvent();
        amOut.setEmployeeShift(amAssignment);
        amOut.setEventType(AttendanceEventType.CHECK_OUT);
        amOut.setTimestamp(LocalDateTime.of(workDate, LocalTime.of(11, 50)));

        when(attendanceEventRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(List.of(amIn, amOut));
        when(attendanceAdjustmentRepository.findByEmployeeIdAndWorkDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedLeavesForEmployeeInRange(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AttendanceRecordResponse response = attendanceCalculator.calculateDayAttendance(employee, workDate);

        assertEquals("LATE_AND_EARLY", response.getStatus());
        assertEquals("ABSENT_UNEXCUSED", response.getShiftBreakdowns().get(1).getStatus());
    }
}

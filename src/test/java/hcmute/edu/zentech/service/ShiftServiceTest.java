package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.shift.BulkShiftUpdateDto;
import hcmute.edu.zentech.dto.shift.CopyWeekDto;
import hcmute.edu.zentech.dto.shift.EmployeeShiftDto;
import hcmute.edu.zentech.mapper.ShiftMapper;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.EmployeeShift;
import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.model.ShiftType;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.AttendanceEventRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.EmployeeShiftRepository;
import hcmute.edu.zentech.repository.PayPeriodRepository;
import hcmute.edu.zentech.repository.ScheduleAdjustmentRepository;
import hcmute.edu.zentech.repository.ShiftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private EmployeeShiftRepository employeeShiftRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ShiftMapper shiftMapper;
    @Mock
    private PayPeriodRepository payPeriodRepository;
    @Mock
    private AttendanceEventRepository attendanceEventRepository;
    @Mock
    private ScheduleAdjustmentRepository scheduleAdjustmentRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AttendanceCalculator attendanceCalculator;
    @Spy
    private ShiftOverlapService shiftOverlapService = new ShiftOverlapService();

    @InjectMocks
    private ShiftService shiftService;

    private final LocalDate workDate = LocalDate.of(2099, 1, 5);

    @Test
    void assignSingleShift_blocksWhenScheduledTimesDoNotOverlapButCaptureWindowsOverlap() {
        Employee employee = employee();
        Shift existing = shift("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 60);
        Shift incoming = shift("Ca chiều", LocalTime.of(12, 30), LocalTime.of(17, 0), 0, 60);
        EmployeeShift existingAssignment = assignment(employee, existing, workDate);
        EmployeeShiftDto dto = assignDto(employee.getId(), incoming.getId(), workDate);

        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(existingAssignment));

        assertThrows(RuntimeException.class, () -> shiftService.assignSingleShift(dto));

        verify(employeeShiftRepository, never()).save(any(EmployeeShift.class));
    }

    @Test
    void assignSingleShift_blocksWhenCaptureWindowsTouchBoundary() {
        Employee employee = employee();
        Shift existing = shift("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 60);
        Shift incoming = shift("Ca chiều", LocalTime.of(13, 30), LocalTime.of(17, 0), 30, 60);
        EmployeeShiftDto dto = assignDto(employee.getId(), incoming.getId(), workDate);

        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(assignment(employee, existing, workDate)));

        assertThrows(RuntimeException.class, () -> shiftService.assignSingleShift(dto));

        verify(employeeShiftRepository, never()).save(any(EmployeeShift.class));
    }

    @Test
    void assignSingleShift_allowsSeparatedCaptureWindows() {
        Employee employee = employee();
        Shift existing = shift("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 60);
        Shift incoming = shift("Ca chiều", LocalTime.of(14, 0), LocalTime.of(17, 0), 30, 60);
        EmployeeShiftDto dto = assignDto(employee.getId(), incoming.getId(), workDate);

        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(assignment(employee, existing, workDate)));
        when(payPeriodRepository.findPeriodActiveAt(workDate)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> shiftService.assignSingleShift(dto));

        verify(employeeShiftRepository).save(any(EmployeeShift.class));
    }

    @Test
    void assignSingleShift_allowsOffShiftWithoutOverlapCheck() {
        Employee employee = employee();
        Shift offShift = new Shift();
        offShift.setId(UUID.randomUUID());
        offShift.setName("Nghỉ");
        offShift.setType(ShiftType.OFF);
        EmployeeShiftDto dto = assignDto(employee.getId(), offShift.getId(), workDate);

        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(offShift.getId())).thenReturn(Optional.of(offShift));
        when(payPeriodRepository.findPeriodActiveAt(workDate)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> shiftService.assignSingleShift(dto));

        verify(employeeShiftRepository).save(any(EmployeeShift.class));
    }

    @Test
    void bulkAssign_abortsWholeRequestWhenAnyEmployeeDateOverlaps() {
        Employee employee = employee();
        Shift existing = shift("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 60);
        Shift incoming = shift("Ca chiều", LocalTime.of(13, 30), LocalTime.of(17, 0), 30, 60);
        BulkShiftUpdateDto dto = bulkDto(employee.getId(), incoming.getId(), workDate, workDate);

        when(shiftRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(employeeRepository.findAllById(List.of(employee.getId()))).thenReturn(List.of(employee));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of(assignment(employee, existing, workDate)));

        assertThrows(RuntimeException.class, () -> shiftService.bulkAssignShifts(dto));

        verify(employeeShiftRepository, never()).saveAll(anyList());
    }

    @Test
    void bulkAssign_savesAllWhenBatchHasNoOverlap() {
        Employee employee = employee();
        Shift incoming = shift("Ca chiều", LocalTime.of(14, 0), LocalTime.of(17, 0), 30, 60);
        BulkShiftUpdateDto dto = bulkDto(employee.getId(), incoming.getId(), workDate, workDate);

        when(shiftRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(employeeRepository.findAllById(List.of(employee.getId()))).thenReturn(List.of(employee));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate))
                .thenReturn(List.of());
        when(payPeriodRepository.findPeriodActiveAt(workDate)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> shiftService.bulkAssignShifts(dto));

        verify(employeeShiftRepository).saveAll(anyList());
    }

    @Test
    void copyWeek_abortsBeforeDeletingDestinationWhenCopiedBatchOverlapsInternally() {
        Employee employee = employee();
        LocalDate sourceDate = LocalDate.of(2099, 1, 1);
        CopyWeekDto dto = copyDto(sourceDate, sourceDate.plusDays(6), workDate, workDate.plusDays(6));
        Shift first = shift("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 60);
        Shift second = shift("Ca chiều", LocalTime.of(13, 30), LocalTime.of(17, 0), 30, 60);

        when(employeeShiftRepository.findAll()).thenReturn(List.of(
                assignment(employee, first, sourceDate),
                assignment(employee, second, sourceDate)
        ));

        assertThrows(RuntimeException.class, () -> shiftService.copyWeeklySchedule(dto));

        verify(employeeShiftRepository, never()).deleteByEmployeeIdInAndWorkDateBetween(anyList(), any(), any());
        verify(employeeShiftRepository, never()).saveAll(anyList());
    }

    @Test
    void copyWeek_deletesDestinationAndSavesWhenCopiedBatchIsClean() {
        Employee employee = employee();
        LocalDate sourceDate = LocalDate.of(2099, 1, 1);
        CopyWeekDto dto = copyDto(sourceDate, sourceDate.plusDays(6), workDate, workDate.plusDays(6));
        Shift shift = shift("Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 30, 60);

        when(employeeShiftRepository.findAll()).thenReturn(List.of(assignment(employee, shift, sourceDate)));
        when(scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(eq(employee.getId()), eq(workDate), eq(workDate)))
                .thenReturn(List.of());
        when(payPeriodRepository.findPeriodActiveAt(workDate)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> shiftService.copyWeeklySchedule(dto));

        verify(employeeShiftRepository).deleteByEmployeeIdInAndWorkDateBetween(List.of(employee.getId()), workDate, workDate.plusDays(6));
        verify(employeeShiftRepository).saveAll(anyList());
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFullName("Nguyễn Văn A");
        return employee;
    }

    private Shift shift(String name, LocalTime start, LocalTime end, int earlyCheckInMinutes, int lateCheckOutMinutes) {
        Shift shift = new Shift();
        shift.setId(UUID.randomUUID());
        shift.setName(name);
        shift.setType(ShiftType.NORMAL);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setEarlyCheckInMinutes(earlyCheckInMinutes);
        shift.setLateCheckOutMinutes(lateCheckOutMinutes);
        return shift;
    }

    private EmployeeShift assignment(Employee employee, Shift shift, LocalDate date) {
        EmployeeShift assignment = new EmployeeShift();
        assignment.setId(UUID.randomUUID());
        assignment.setEmployee(employee);
        assignment.setShift(shift);
        assignment.setWorkDate(date);
        return assignment;
    }

    private EmployeeShiftDto assignDto(UUID employeeId, UUID shiftId, LocalDate date) {
        EmployeeShiftDto dto = new EmployeeShiftDto();
        dto.setEmployeeId(employeeId);
        dto.setShiftId(shiftId);
        dto.setWorkDate(date);
        return dto;
    }

    private BulkShiftUpdateDto bulkDto(UUID employeeId, UUID shiftId, LocalDate startDate, LocalDate endDate) {
        BulkShiftUpdateDto dto = new BulkShiftUpdateDto();
        dto.setEmployeeIds(List.of(employeeId));
        dto.setSelectAll(false);
        dto.setShiftId(shiftId);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        return dto;
    }

    private CopyWeekDto copyDto(LocalDate fromStart, LocalDate fromEnd, LocalDate toStart, LocalDate toEnd) {
        CopyWeekDto dto = new CopyWeekDto();
        dto.setFromWeekStartDate(fromStart);
        dto.setFromWeekEndDate(fromEnd);
        dto.setToWeekStartDate(toStart);
        dto.setToWeekEndDate(toEnd);
        return dto;
    }
}

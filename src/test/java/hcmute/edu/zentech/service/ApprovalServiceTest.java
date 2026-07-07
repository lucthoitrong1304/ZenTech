package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.LeaveRequestCreateRequest;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.security.SecurityContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {
    @Mock
    private AttendanceAdjustmentRepository attendanceAdjustmentRepository;
    @Mock
    private ShiftSwapRequestRepository shiftSwapRequestRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private PayPeriodRepository payPeriodRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveManagementService leaveManagementService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private EmployeeShiftRepository employeeShiftRepository;
    @Mock
    private ShiftOverlapService shiftOverlapService;
    @Mock
    private AttendanceEventRepository attendanceEventRepository;

    @InjectMocks
    private ApprovalService approvalService;

    private MockedStatic<SecurityContextUtils> securityContextUtils;
    private UUID requesterAccountId;
    private Employee requester;
    private Employee targetEmployee;
    private Shift sourceShift;
    private Shift targetShift;
    private EmployeeShift sourceAssignment;
    private EmployeeShift targetAssignment;
    private LocalDate sourceDate;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        requesterAccountId = UUID.randomUUID();
        requester = employee("Requester", requesterAccountId);
        targetEmployee = employee("Target", UUID.randomUUID());
        sourceDate = LocalDate.now().plusDays(1);
        targetDate = LocalDate.now().plusDays(2);
        sourceShift = shift("AM", LocalTime.of(9, 0), LocalTime.of(12, 0));
        targetShift = shift("PM", LocalTime.of(13, 0), LocalTime.of(17, 0));
        sourceAssignment = assignment(requester, sourceShift, sourceDate);
        targetAssignment = assignment(targetEmployee, targetShift, targetDate);

        securityContextUtils = Mockito.mockStatic(SecurityContextUtils.class);
        securityContextUtils.when(SecurityContextUtils::getCurrentUserId).thenReturn(requesterAccountId);

        lenient().when(payPeriodRepository.findPeriodActiveAt(any())).thenReturn(Optional.empty());
        lenient().when(employeeRepository.findByUserInfo_Id(requesterAccountId)).thenReturn(Optional.of(requester));
        lenient().when(employeeRepository.findById(targetEmployee.getId())).thenReturn(Optional.of(targetEmployee));
        lenient().when(employeeRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        lenient().when(shiftRepository.findById(sourceShift.getId())).thenReturn(Optional.of(sourceShift));
        lenient().when(shiftRepository.findById(targetShift.getId())).thenReturn(Optional.of(targetShift));
        lenient().when(accountUserRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(accountUserRepository.findByRoleInAndIsActiveTrue(any())).thenReturn(List.of());
        lenient().when(shiftOverlapService.hasCaptureWindow(any())).thenReturn(true);
        lenient().when(shiftOverlapService.captureWindow(any(), any()))
                .thenAnswer(invocation -> new ShiftOverlapService.CaptureWindow(
                        invocation.getArgument(0, LocalDate.class).atTime(0, 0),
                        invocation.getArgument(0, LocalDate.class).atTime(1, 0)
                ));
        lenient().when(shiftOverlapService.overlapsInclusive(any(), any())).thenReturn(false);
        lenient().when(shiftSwapRequestRepository.save(any(ShiftSwapRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        securityContextUtils.close();
    }

    @Test
    void requestShiftSwap_blocksCoverWhenSourceShiftHasAttendanceEvent() {
        ShiftSwapRequest request = coverRequest();
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(attendanceEventRepository.existsByEmployeeShift_Id(sourceAssignment.getId())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.requestShiftSwap(request));

        assertTrue(ex.getMessage().contains("đã phát sinh dữ liệu chấm công"));
        verify(shiftSwapRequestRepository, never()).save(any());
    }

    @Test
    void requestShiftSwap_blocksSwapWhenRequesterShiftHasAttendanceEvent() {
        ShiftSwapRequest request = swapRequest();
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(attendanceEventRepository.existsByEmployeeShift_Id(sourceAssignment.getId())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.requestShiftSwap(request));

        assertTrue(ex.getMessage().contains("đã phát sinh dữ liệu chấm công"));
        verify(shiftSwapRequestRepository, never()).save(any());
    }

    @Test
    void requestShiftSwap_blocksSwapWhenTargetShiftHasAttendanceEvent() {
        ShiftSwapRequest request = swapRequest();
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(targetEmployee.getId(), targetDate))
                .thenReturn(List.of(targetAssignment));
        when(attendanceEventRepository.existsByEmployeeShift_Id(sourceAssignment.getId())).thenReturn(false);
        when(attendanceEventRepository.existsByEmployeeShift_Id(targetAssignment.getId())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.requestShiftSwap(request));

        assertTrue(ex.getMessage().contains("đã phát sinh dữ liệu chấm công"));
        verify(shiftSwapRequestRepository, never()).save(any());
    }

    @Test
    void requestShiftSwap_allowsSwapWhenAssignmentsHaveNoAttendanceEventsAndNoOverlap() {
        ShiftSwapRequest request = swapRequest();
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(targetEmployee.getId(), targetDate))
                .thenReturn(List.of(targetAssignment));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(targetEmployee.getId(), sourceDate))
                .thenReturn(List.of());
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), targetDate))
                .thenReturn(List.of());
        when(attendanceEventRepository.existsByEmployeeShift_Id(sourceAssignment.getId())).thenReturn(false);
        when(attendanceEventRepository.existsByEmployeeShift_Id(targetAssignment.getId())).thenReturn(false);

        ShiftSwapRequest saved = approvalService.requestShiftSwap(request);

        assertNotNull(saved);
        assertEquals(ApprovalStatus.PENDING, saved.getStatus());
        verify(shiftSwapRequestRepository).save(request);
    }

    @Test
    void requestShiftSwap_stillBlocksWhenShiftAlreadyStarted() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        ShiftSwapRequest request = coverRequest();
        request.setWorkDate(pastDate);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.requestShiftSwap(request));

        assertTrue(ex.getMessage().contains("đã bắt đầu hoặc đã qua"));
        verify(employeeShiftRepository, never()).findByEmployeeIdAndWorkDate(any(), any());
        verify(shiftSwapRequestRepository, never()).save(any());
    }

    @Test
    void approveShiftSwap_blocksApprovalWhenSourceShiftHasAttendanceEvent() {
        ShiftSwapRequest request = approvedCandidate(SwapRequestType.COVER);
        when(shiftSwapRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(attendanceEventRepository.existsByEmployeeShift_Id(sourceAssignment.getId())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.approveShiftSwap(request.getId(), ApprovalStatus.APPROVED));

        assertTrue(ex.getMessage().contains("đã phát sinh dữ liệu chấm công"));
        verify(shiftSwapRequestRepository, never()).save(any());
    }

    @Test
    void approveShiftSwap_blocksSwapApprovalWhenTargetShiftHasAttendanceEvent() {
        ShiftSwapRequest request = approvedCandidate(SwapRequestType.SWAP);
        request.setTargetWorkDate(targetDate);
        request.setTargetShift(targetShift);
        when(shiftSwapRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(targetEmployee.getId(), targetDate))
                .thenReturn(List.of(targetAssignment));
        when(attendanceEventRepository.existsByEmployeeShift_Id(sourceAssignment.getId())).thenReturn(false);
        when(attendanceEventRepository.existsByEmployeeShift_Id(targetAssignment.getId())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.approveShiftSwap(request.getId(), ApprovalStatus.APPROVED));

        assertTrue(ex.getMessage().contains("đã phát sinh dữ liệu chấm công"));
        verify(shiftSwapRequestRepository, never()).save(any());
    }

    @Test
    void approveShiftSwap_doesNotCheckAttendanceEventsWhenRejecting() {
        ShiftSwapRequest request = approvedCandidate(SwapRequestType.COVER);
        when(shiftSwapRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ShiftSwapRequest saved = approvalService.approveShiftSwap(request.getId(), ApprovalStatus.REJECTED);

        assertEquals(ApprovalStatus.REJECTED, saved.getStatus());
        verify(attendanceEventRepository, never()).existsByEmployeeShift_Id(any());
        verify(shiftSwapRequestRepository).save(request);
    }

    @Test
    void requestLeave_allowsAfkInsideAssignedShiftWhenWfhExists() {
        LeaveType afkType = leaveType(LeaveManagementService.DEFAULT_AFK_CODE, LeaveTypeUnit.HOUR);
        LeaveRequest existingWfh = activeLeave(leaveType(LeaveManagementService.DEFAULT_WFH_CODE, LeaveTypeUnit.DAY), sourceDate);
        LeaveRequestCreateRequest request = leaveRequest(afkType, sourceDate, LocalTime.of(9, 0), LocalTime.of(10, 0));

        when(leaveTypeRepository.findById(afkType.getId())).thenReturn(Optional.of(afkType));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(leaveRequestRepository.findActiveLeavesForEmployeeInRangeWithDetails(eq(requester.getId()), eq(sourceDate), eq(sourceDate), anyList()))
                .thenReturn(List.of(existingWfh));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequest saved = approvalService.requestLeave(request);

        assertEquals(ApprovalStatus.PENDING, saved.getStatus());
        assertEquals(afkType, saved.getLeaveType());
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
    }

    @Test
    void requestLeave_blocksAfkOutsideAssignedShift() {
        LeaveType afkType = leaveType(LeaveManagementService.DEFAULT_AFK_CODE, LeaveTypeUnit.HOUR);
        LeaveRequestCreateRequest request = leaveRequest(afkType, sourceDate, LocalTime.of(18, 0), LocalTime.of(19, 0));

        when(leaveTypeRepository.findById(afkType.getId())).thenReturn(Optional.of(afkType));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.requestLeave(request));

        assertTrue(ex.getMessage().contains("AFK"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void requestLeave_blocksAfkWhenNghiShiftOverlaps() {
        LeaveType afkType = leaveType(LeaveManagementService.DEFAULT_AFK_CODE, LeaveTypeUnit.HOUR);
        LeaveRequest existingNghi = activeLeave(leaveType(LeaveManagementService.DEFAULT_NGHI_CODE, LeaveTypeUnit.DAY), sourceDate);
        existingNghi.setTargetShifts(List.of(sourceShift));
        LeaveRequestCreateRequest request = leaveRequest(afkType, sourceDate, LocalTime.of(9, 30), LocalTime.of(10, 0));

        when(leaveTypeRepository.findById(afkType.getId())).thenReturn(Optional.of(afkType));
        when(employeeShiftRepository.findByEmployeeIdAndWorkDate(requester.getId(), sourceDate))
                .thenReturn(List.of(sourceAssignment));
        when(leaveRequestRepository.findActiveLeavesForEmployeeInRangeWithDetails(eq(requester.getId()), eq(sourceDate), eq(sourceDate), anyList()))
                .thenReturn(List.of(existingNghi));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> approvalService.requestLeave(request));

        assertTrue(ex.getMessage().contains("AFK"));
        verify(leaveRequestRepository, never()).save(any());
    }

    private ShiftSwapRequest coverRequest() {
        ShiftSwapRequest request = new ShiftSwapRequest();
        request.setTargetEmployee(targetEmployee);
        request.setWorkDate(sourceDate);
        request.setShift(sourceShift);
        request.setType(SwapRequestType.COVER);
        request.setReason("Need cover");
        return request;
    }

    private ShiftSwapRequest swapRequest() {
        ShiftSwapRequest request = coverRequest();
        request.setType(SwapRequestType.SWAP);
        request.setTargetWorkDate(targetDate);
        request.setTargetShift(targetShift);
        return request;
    }

    private ShiftSwapRequest approvedCandidate(SwapRequestType type) {
        ShiftSwapRequest request = new ShiftSwapRequest();
        request.setId(UUID.randomUUID());
        request.setRequester(requester);
        request.setTargetEmployee(targetEmployee);
        request.setWorkDate(sourceDate);
        request.setShift(sourceShift);
        request.setType(type);
        request.setReason("Need approval");
        request.setStatus(ApprovalStatus.PENDING);
        return request;
    }

    private Employee employee(String name, UUID accountId) {
        AccountUser account = new AccountUser();
        account.setId(accountId);

        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFullName(name);
        employee.setUserInfo(account);
        return employee;
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

    private EmployeeShift assignment(Employee employee, Shift shift, LocalDate workDate) {
        EmployeeShift assignment = new EmployeeShift();
        assignment.setId(UUID.randomUUID());
        assignment.setEmployee(employee);
        assignment.setShift(shift);
        assignment.setWorkDate(workDate);
        return assignment;
    }

    private LeaveType leaveType(String code, LeaveTypeUnit unit) {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(UUID.randomUUID());
        leaveType.setCode(code);
        leaveType.setName(code);
        leaveType.setUnit(unit);
        leaveType.setActive(true);
        return leaveType;
    }

    private LeaveRequestCreateRequest leaveRequest(LeaveType leaveType, LocalDate date, LocalTime startTime, LocalTime endTime) {
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest();
        request.setLeaveTypeId(leaveType.getId());
        request.setStartDate(date);
        request.setEndDate(date);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setReason("AFK reason");
        return request;
    }

    private LeaveRequest activeLeave(LeaveType leaveType, LocalDate date) {
        LeaveRequest request = new LeaveRequest();
        request.setId(UUID.randomUUID());
        request.setEmployee(requester);
        request.setLeaveType(leaveType);
        request.setStartDate(date);
        request.setEndDate(date);
        request.setStatus(ApprovalStatus.PENDING);
        return request;
    }
}

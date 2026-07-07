package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.LeaveRequestCreateRequest;
import hcmute.edu.zentech.dto.response.EmployeeLeaveQuotaResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalService {
    private final AttendanceAdjustmentRepository attendanceAdjustmentRepository;
    private final ShiftSwapRequestRepository shiftSwapRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayPeriodRepository payPeriodRepository;
    private final AccountUserRepository accountUserRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveManagementService leaveManagementService;
    private final NotificationService notificationService;
    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final ShiftOverlapService shiftOverlapService;
    private final AttendanceEventRepository attendanceEventRepository;

    private void checkLock(LocalDate date) {
        Optional<PayPeriod> p = payPeriodRepository.findPeriodActiveAt(date);
        if (p.isPresent() && p.get().isLocked()) {
            throw new RuntimeException("Kỳ công chứa ngày " + date + " đã bị khóa. Không thể thay đổi dữ liệu.");
        }
    }

    // --- Attendance Adjustment ---
    @Transactional
    public AttendanceAdjustment requestAttendanceAdjustment(AttendanceAdjustment request) {
        checkLock(request.getWorkDate());
        
        UUID userId = SecurityContextUtils.getCurrentUserId();
        AccountUser user = userId != null ? accountUserRepository.findById(userId).orElse(null) : null;
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;

        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }

        request.setEmployee(employee);
        request.setRequestedBy(user);
        request.setRequestedAt(LocalDateTime.now());
        request.setStatus(ApprovalStatus.PENDING);
        
        AttendanceAdjustment saved = attendanceAdjustmentRepository.save(request);

        // Notify admins/managers/owners
        List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
        );
        String title = "Yêu cầu chỉnh sửa công mới";
        String content = String.format("Nhân viên %s đã gửi yêu cầu chỉnh sửa công ngày %s.", 
                employee.getFullName(), request.getWorkDate());
        for (AccountUser mgr : managers) {
            notificationService.createNotification(
                    mgr.getId(), 
                    title, 
                    content, 
                    NotificationType.REQUEST_SUBMITTED, 
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional
    public AttendanceAdjustment approveAttendanceAdjustment(UUID id, ApprovalStatus status, String rejectionReason) {
        AttendanceAdjustment request = attendanceAdjustmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Yêu cầu không tồn tại."));
        
        checkLock(request.getWorkDate());

        UUID userId = SecurityContextUtils.getCurrentUserId();
        AccountUser user = userId != null ? accountUserRepository.findById(userId).orElse(null) : null;

        boolean isCancelRequest = request.getStatus() == ApprovalStatus.CANCEL_PENDING;

        if (isCancelRequest) {
            if (status == ApprovalStatus.APPROVED) {
                request.setStatus(ApprovalStatus.CANCELLED);
            } else {
                request.setStatus(ApprovalStatus.APPROVED);
            }
        } else {
            request.setStatus(status);
            if (status == ApprovalStatus.REJECTED) {
                request.setRejectionReason(rejectionReason);
            }
        }

        request.setApprovedBy(user);
        request.setApprovedAt(LocalDateTime.now());

        AttendanceAdjustment saved = attendanceAdjustmentRepository.save(request);

        // Notify employee
        if (saved.getEmployee() != null && saved.getEmployee().getUserInfo() != null) {
            UUID employeeAccountId = saved.getEmployee().getUserInfo().getId();
            String title = isCancelRequest ? "Kết quả duyệt hủy yêu cầu chỉnh sửa công" : "Kết quả duyệt chỉnh sửa công";
            String statusStr;
            if (isCancelRequest) {
                statusStr = saved.getStatus() == ApprovalStatus.CANCELLED ? "ĐÃ ĐƯỢC DUYỆT HỦY" : "BỊ TỪ CHỐI HỦY";
            } else {
                statusStr = saved.getStatus() == ApprovalStatus.APPROVED ? "ĐÃ DUYỆT" : "BỊ TỪ CHỐI";
            }
            String content = String.format("Yêu cầu chỉnh sửa công ngày %s của bạn đã %s.", 
                    saved.getWorkDate(), statusStr);
            notificationService.createNotification(
                    employeeAccountId, 
                    title, 
                    content, 
                    (saved.getStatus() == ApprovalStatus.APPROVED || saved.getStatus() == ApprovalStatus.CANCELLED) 
                            ? NotificationType.REQUEST_APPROVED : NotificationType.REQUEST_REJECTED, 
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AttendanceAdjustment> getPendingAttendanceAdjustments() {
        return attendanceAdjustmentRepository.findByStatusIn(List.of(ApprovalStatus.PENDING, ApprovalStatus.CANCEL_PENDING));
    }

    // --- Shift Swap / Cover Requests ---
    @Transactional
    public ShiftSwapRequest requestShiftSwap(ShiftSwapRequest request) {
        checkLock(request.getWorkDate());
        if (request.getTargetWorkDate() != null) {
            checkLock(request.getTargetWorkDate());
        }

        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;

        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên yêu cầu.");
        }

        Employee targetEmployee = employeeRepository.findById(request.getTargetEmployee().getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên làm thay/đổi ca."));

        Shift shift = shiftRepository.findById(request.getShift().getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca yêu cầu."));

        Shift targetShift = null;
        if (request.getTargetShift() != null && request.getTargetShift().getId() != null) {
            targetShift = shiftRepository.findById(request.getTargetShift().getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ca đổi."));
        }
        if (request.getType() == SwapRequestType.SWAP && (request.getTargetWorkDate() == null || targetShift == null)) {
            throw new RuntimeException("Vui lòng chọn ngày và ca đối ứng khi tạo yêu cầu đổi ca.");
        }

        // 1. Time constraints check (must request before shift start time)
        LocalDateTime now = LocalDateTime.now();
        if (shift.getStartTime() != null) {
            LocalDateTime shiftStart = LocalDateTime.of(request.getWorkDate(), shift.getStartTime());
            if (!now.isBefore(shiftStart)) {
                throw new RuntimeException("Không thể tạo yêu cầu cho ca làm việc đã bắt đầu hoặc đã qua.");
            }
        }
        if (request.getType() == SwapRequestType.SWAP && targetShift != null && targetShift.getStartTime() != null && request.getTargetWorkDate() != null) {
            LocalDateTime targetShiftStart = LocalDateTime.of(request.getTargetWorkDate(), targetShift.getStartTime());
            if (!now.isBefore(targetShiftStart)) {
                throw new RuntimeException("Không thể tạo yêu cầu cho ca làm việc đối ứng đã bắt đầu hoặc đã qua.");
            }
        }

        EmployeeShift sourceAssignment = resolveAssignment(employee.getId(), request.getWorkDate(), shift, "ca nguồn");
        assertNoAttendanceEvents(sourceAssignment, "ca nguồn");

        if (request.getType() == SwapRequestType.SWAP && targetShift != null && request.getTargetWorkDate() != null) {
            EmployeeShift targetAssignment = resolveAssignment(
                    targetEmployee.getId(),
                    request.getTargetWorkDate(),
                    targetShift,
                    "ca đối ứng"
            );
            assertNoAttendanceEvents(targetAssignment, "ca đối ứng");
        }

        // 2. Overlap checks
        // Target employee must not have overlapping shift on workDate with shift
        validateNoOverlap(targetEmployee.getId(), request.getWorkDate(), shift);

        // For SWAP, requester must not have overlapping shift on targetWorkDate with targetShift
        if (request.getType() == SwapRequestType.SWAP && targetShift != null && request.getTargetWorkDate() != null) {
            validateNoOverlap(employee.getId(), request.getTargetWorkDate(), targetShift);
        }

        request.setRequester(employee);
        request.setTargetEmployee(targetEmployee);
        request.setShift(shift);
        request.setTargetShift(targetShift);
        request.setRequestedAt(LocalDateTime.now());
        request.setStatus(ApprovalStatus.PENDING);

        ShiftSwapRequest saved = shiftSwapRequestRepository.save(request);

        String reqName = employee.getFullName();
        String tgtName = targetEmployee.getFullName();

        // 1. Notify target employee
        if (targetEmployee.getUserInfo() != null) {
            String title = "Yêu cầu đổi ca từ đồng nghiệp";
            String content = String.format("Đồng nghiệp %s muốn đổi ca với bạn vào ngày %s.", reqName, saved.getWorkDate());
            notificationService.createNotification(
                    targetEmployee.getUserInfo().getId(),
                    title,
                    content,
                    NotificationType.REQUEST_SUBMITTED,
                    saved.getId()
            );
        }

        // 2. Notify managers/admins/owners
        List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
        );
        String title = "Yêu cầu đổi ca mới";
        String content = String.format("Nhân viên %s đã gửi yêu cầu đổi ca với %s vào ngày %s.", reqName, tgtName, saved.getWorkDate());
        for (AccountUser mgr : managers) {
            notificationService.createNotification(
                    mgr.getId(),
                    title,
                    content,
                    NotificationType.REQUEST_SUBMITTED,
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional
    public ShiftSwapRequest approveShiftSwap(UUID id, ApprovalStatus status) {
        ShiftSwapRequest request = shiftSwapRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Yêu cầu đổi ca không tồn tại."));

        checkLock(request.getWorkDate());
        if (request.getTargetWorkDate() != null) {
            checkLock(request.getTargetWorkDate());
        }

        UUID userId = SecurityContextUtils.getCurrentUserId();
        AccountUser user = userId != null ? accountUserRepository.findById(userId).orElse(null) : null;

        boolean isCancelRequest = request.getStatus() == ApprovalStatus.CANCEL_PENDING;
        boolean approvingPendingRequest = !isCancelRequest
                && request.getStatus() == ApprovalStatus.PENDING
                && status == ApprovalStatus.APPROVED;

        if (approvingPendingRequest) {
            assertSwapAssignmentsHaveNoAttendanceEvents(request);
        }

        if (isCancelRequest) {
            if (status == ApprovalStatus.APPROVED) {
                request.setStatus(ApprovalStatus.CANCELLED);
            } else {
                request.setStatus(ApprovalStatus.APPROVED);
            }
        } else {
            request.setStatus(status);
        }

        request.setApprovedBy(user);
        request.setApprovedAt(LocalDateTime.now());

        ShiftSwapRequest saved = shiftSwapRequestRepository.save(request);

        Employee reqEmp = employeeRepository.findById(saved.getRequester().getId()).orElse(null);
        Employee tgtEmp = employeeRepository.findById(saved.getTargetEmployee().getId()).orElse(null);

        String title = isCancelRequest ? "Kết quả duyệt hủy yêu cầu đổi ca" : "Kết quả duyệt đổi ca";
        String statusStr;
        if (isCancelRequest) {
            statusStr = saved.getStatus() == ApprovalStatus.CANCELLED ? "ĐÃ ĐƯỢC DUYỆT HỦY" : "BỊ TỪ CHỐI HỦY";
        } else {
            statusStr = saved.getStatus() == ApprovalStatus.APPROVED ? "ĐÃ DUYỆT" : "BỊ TỪ CHỐI";
        }
        String content = String.format("Yêu cầu đổi ca ngày %s của bạn đã %s.", saved.getWorkDate(), statusStr);
        NotificationType notiType = (saved.getStatus() == ApprovalStatus.APPROVED || saved.getStatus() == ApprovalStatus.CANCELLED) 
                ? NotificationType.REQUEST_APPROVED : NotificationType.REQUEST_REJECTED;

        // 1. Notify requester
        if (reqEmp != null && reqEmp.getUserInfo() != null) {
            notificationService.createNotification(
                    reqEmp.getUserInfo().getId(),
                    title,
                    content,
                    notiType,
                    saved.getId()
            );
        }

        // 2. Notify targetEmployee
        if (tgtEmp != null && tgtEmp.getUserInfo() != null) {
            if (isCancelRequest) {
                if (saved.getStatus() == ApprovalStatus.CANCELLED) {
                    String tgtContent = String.format("Lịch làm việc ngày %s của bạn đã được khôi phục lại do yêu cầu đổi ca/làm thay đã bị hủy.", saved.getWorkDate());
                    notificationService.createNotification(
                            tgtEmp.getUserInfo().getId(),
                            title,
                            tgtContent,
                            NotificationType.REQUEST_APPROVED,
                            saved.getId()
                    );
                }
            } else {
                if (saved.getStatus() == ApprovalStatus.APPROVED) {
                    String tgtContent = String.format("Lịch làm việc của bạn ngày %s đã thay đổi do yêu cầu đổi ca đã được phê duyệt.", saved.getWorkDate());
                    notificationService.createNotification(
                            tgtEmp.getUserInfo().getId(),
                            title,
                            tgtContent,
                            NotificationType.REQUEST_APPROVED,
                            saved.getId()
                    );
                }
            }
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> getPendingShiftSwaps() {
        return shiftSwapRequestRepository.findByStatusIn(List.of(ApprovalStatus.PENDING, ApprovalStatus.CANCEL_PENDING));
    }

    // --- Leave Requests ---
    @Transactional
    public LeaveRequest requestLeave(LeaveRequestCreateRequest request) {
        // Check lock for start date and end date
        checkLock(request.getStartDate());
        checkLock(request.getEndDate());

        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;

        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new RuntimeException("Loại phép không tồn tại."));
        validateLeaveRequestShape(leaveType, request);

        LeaveRequest entity = new LeaveRequest();
        entity.setEmployee(employee);
        entity.setLeaveType(leaveType);
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setReason(request.getReason().trim());
        entity.setRequestedAt(LocalDateTime.now());
        entity.setStatus(ApprovalStatus.PENDING);

        if (isAfkLeave(leaveType) && request.getShiftIds() != null && !request.getShiftIds().isEmpty()) {
            throw new RuntimeException("AFK không hỗ trợ chọn ca. Vui lòng nhập khung giờ AFK.");
        }

        if (request.getShiftIds() != null && !request.getShiftIds().isEmpty()) {
            if (!request.getStartDate().equals(request.getEndDate())) {
                throw new RuntimeException("Chỉ hỗ trợ chọn ca nghỉ khi nghỉ phép trong cùng một ngày.");
            }
            List<Shift> shifts = shiftRepository.findAllById(request.getShiftIds());
            if (shifts.size() != request.getShiftIds().size()) {
                throw new RuntimeException("Một số ca làm việc không tồn tại.");
            }
            entity.setTargetShifts(shifts);
        }

        validateAfkWithinAssignedShift(employee, entity);
        validateLeaveRequestDoesNotOverlap(employee, entity);
        leaveManagementService.ensureQuotas(employee, request.getStartDate().getYear());

        LeaveRequest saved = leaveRequestRepository.save(entity);

        // Notify managers/admins/owners
        List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
        );
        String title = "Yêu cầu nghỉ phép mới";
        String content = String.format("Nhân viên %s đã gửi yêu cầu nghỉ phép từ ngày %s đến ngày %s.", 
                employee.getFullName(), request.getStartDate(), request.getEndDate());
        for (AccountUser mgr : managers) {
            notificationService.createNotification(
                    mgr.getId(),
                    title,
                    content,
                    NotificationType.REQUEST_SUBMITTED,
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional
    public LeaveRequest approveLeave(UUID id, ApprovalStatus status) {
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Yêu cầu nghỉ phép không tồn tại."));

        checkLock(request.getStartDate());
        checkLock(request.getEndDate());

        boolean isCancelRequest = request.getStatus() == ApprovalStatus.CANCEL_PENDING;

        UUID userId = SecurityContextUtils.getCurrentUserId();
        AccountUser user = userId != null ? accountUserRepository.findById(userId).orElse(null) : null;

        if (isCancelRequest) {
            if (status == ApprovalStatus.APPROVED) {
                request.setStatus(ApprovalStatus.CANCELLED);
            } else {
                request.setStatus(ApprovalStatus.APPROVED);
            }
        } else {
            request.setStatus(status);
        }

        request.setApprovedBy(user);
        request.setApprovedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify employee
        if (saved.getEmployee() != null && saved.getEmployee().getUserInfo() != null) {
            UUID employeeAccountId = saved.getEmployee().getUserInfo().getId();
            String title = isCancelRequest ? "Kết quả duyệt hủy nghỉ phép" : "Kết quả duyệt nghỉ phép";
            String statusStr;
            if (isCancelRequest) {
                statusStr = saved.getStatus() == ApprovalStatus.CANCELLED ? "ĐÃ ĐƯỢC DUYỆT HỦY" : "BỊ TỪ CHỐI HỦY";
            } else {
                statusStr = saved.getStatus() == ApprovalStatus.APPROVED ? "ĐÃ DUYỆT" : "BỊ TỪ CHỐI";
            }
            String content = String.format("Yêu cầu nghỉ phép từ ngày %s đến ngày %s của bạn đã %s.", 
                    saved.getStartDate(), saved.getEndDate(), statusStr);
            notificationService.createNotification(
                    employeeAccountId,
                    title,
                    content,
                    (saved.getStatus() == ApprovalStatus.APPROVED || saved.getStatus() == ApprovalStatus.CANCELLED) 
                            ? NotificationType.REQUEST_APPROVED : NotificationType.REQUEST_REJECTED,
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingLeaves() {
        return leaveRequestRepository.findByStatusIn(List.of(ApprovalStatus.PENDING, ApprovalStatus.CANCEL_PENDING));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getMyLeaves() {
        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;
        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }
        return leaveRequestRepository.findByEmployeeIdOrderByRequestedAtDesc(employee.getId());
    }

    @Transactional
    public List<EmployeeLeaveQuotaResponse> getMyLeaveQuotas(int year) {
        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;
        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }
        return leaveManagementService.getEmployeeQuotas(employee.getId(), year);
    }

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> getMySwaps() {
        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;
        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }
        return shiftSwapRequestRepository.findMySwaps(employee.getId());
    }

    @Transactional(readOnly = true)
    public List<AttendanceAdjustment> getMyAdjustments() {
        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;
        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }
        return attendanceAdjustmentRepository.findByEmployeeIdOrderByRequestedAtDesc(employee.getId());
    }

    private void validateLeaveRequestShape(LeaveType leaveType, LeaveRequestCreateRequest request) {
        if (!leaveType.isActive()) {
            throw new RuntimeException("Loại phép đã bị tắt.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        if (leaveType.getUnit() == LeaveTypeUnit.DAY) {
            return;
        }
        if (!request.getStartDate().equals(request.getEndDate())) {
            throw new RuntimeException("Loại phép theo giờ chỉ được đăng ký trong cùng một ngày.");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new RuntimeException("Vui lòng nhập giờ bắt đầu và kết thúc.");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new RuntimeException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
    }

    private void validateLeaveRequestDoesNotOverlap(Employee employee, LeaveRequest candidate) {
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findActiveLeavesForEmployeeInRangeWithDetails(
                employee.getId(),
                candidate.getStartDate(),
                candidate.getEndDate(),
                activeLeaveStatuses()
        );

        for (LeaveRequest existing : activeLeaves) {
            if (leaveRequestsOverlap(candidate, existing)) {
                throw new RuntimeException(duplicateLeaveMessage(candidate));
            }
        }
    }

    private void validateAfkWithinAssignedShift(Employee employee, LeaveRequest candidate) {
        if (!isAfkLeave(candidate)) {
            return;
        }

        List<EmployeeShift> assignments = employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), candidate.getStartDate());
        boolean inAssignedWorkingShift = assignments.stream()
                .map(EmployeeShift::getShift)
                .filter(Objects::nonNull)
                .filter(shift -> shift.getType() != ShiftType.OFF)
                .filter(shift -> shift.getStartTime() != null && shift.getEndTime() != null)
                .anyMatch(shift -> timeRangeContains(shift.getStartTime(), shift.getEndTime(), candidate.getStartTime(), candidate.getEndTime()));

        if (!inAssignedWorkingShift) {
            throw new RuntimeException("Khung giờ AFK phải nằm trong ca làm việc của bạn.");
        }
    }

    private boolean leaveRequestsOverlap(LeaveRequest candidate, LeaveRequest existing) {
        if (isAfkLeave(candidate)) {
            return afkLeaveOverlaps(candidate, existing);
        }
        if (isWfhLeave(candidate) && isAfkLeave(existing)) {
            return false;
        }
        if (candidate.getLeaveType().getUnit() == LeaveTypeUnit.HOUR) {
            return hourLeaveOverlaps(candidate, existing);
        }
        if (candidate.getTargetShifts() != null && !candidate.getTargetShifts().isEmpty()) {
            return shiftLeaveOverlaps(candidate, existing);
        }
        return true;
    }

    private boolean afkLeaveOverlaps(LeaveRequest candidate, LeaveRequest existing) {
        if (isWfhLeave(existing)) {
            return false;
        }
        if (isAfkLeave(existing)) {
            return timeRangesOverlap(candidate.getStartTime(), candidate.getEndTime(), existing.getStartTime(), existing.getEndTime());
        }
        if (!isNghiLeave(existing)) {
            return false;
        }
        if (existing.getTargetShifts() == null || existing.getTargetShifts().isEmpty()) {
            if (existing.getStartTime() == null || existing.getEndTime() == null) {
                return true;
            }
            return timeRangesOverlap(candidate.getStartTime(), candidate.getEndTime(), existing.getStartTime(), existing.getEndTime());
        }
        return existing.getTargetShifts().stream()
                .filter(shift -> shift != null && shift.getStartTime() != null && shift.getEndTime() != null)
                .anyMatch(shift -> timeRangesOverlap(candidate.getStartTime(), candidate.getEndTime(), shift.getStartTime(), shift.getEndTime()));
    }

    private boolean shiftLeaveOverlaps(LeaveRequest candidate, LeaveRequest existing) {
        if (existing.getTargetShifts() == null || existing.getTargetShifts().isEmpty()) {
            if (existing.getStartTime() != null && existing.getEndTime() != null) {
                return candidate.getTargetShifts().stream()
                        .filter(shift -> shift != null && shift.getStartTime() != null && shift.getEndTime() != null)
                        .anyMatch(shift -> timeRangesOverlap(shift.getStartTime(), shift.getEndTime(), existing.getStartTime(), existing.getEndTime()));
            }
            return true;
        }

        return candidate.getTargetShifts().stream()
                .filter(shift -> shift != null && shift.getId() != null)
                .anyMatch(candidateShift -> existing.getTargetShifts().stream()
                        .filter(existingShift -> existingShift != null && existingShift.getId() != null)
                        .anyMatch(existingShift -> candidateShift.getId().equals(existingShift.getId())));
    }

    private boolean hourLeaveOverlaps(LeaveRequest candidate, LeaveRequest existing) {
        if (existing.getTargetShifts() == null || existing.getTargetShifts().isEmpty()) {
            if (existing.getStartTime() == null || existing.getEndTime() == null) {
                return true;
            }
            return timeRangesOverlap(candidate.getStartTime(), candidate.getEndTime(), existing.getStartTime(), existing.getEndTime());
        }

        return existing.getTargetShifts().stream()
                .filter(shift -> shift != null && shift.getStartTime() != null && shift.getEndTime() != null)
                .anyMatch(shift -> timeRangesOverlap(candidate.getStartTime(), candidate.getEndTime(), shift.getStartTime(), shift.getEndTime()));
    }

    private boolean timeRangesOverlap(LocalTime firstStart, LocalTime firstEnd, LocalTime secondStart, LocalTime secondEnd) {
        if (firstStart == null || firstEnd == null || secondStart == null || secondEnd == null) {
            return false;
        }
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private boolean timeRangeContains(LocalTime containerStart, LocalTime containerEnd, LocalTime innerStart, LocalTime innerEnd) {
        if (containerStart == null || containerEnd == null || innerStart == null || innerEnd == null) {
            return false;
        }
        return !innerStart.isBefore(containerStart) && !innerEnd.isAfter(containerEnd);
    }

    private List<ApprovalStatus> activeLeaveStatuses() {
        return List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED, ApprovalStatus.CANCEL_PENDING);
    }

    private boolean isAfkLeave(LeaveRequest request) {
        return request != null && isAfkLeave(request.getLeaveType());
    }

    private boolean isAfkLeave(LeaveType leaveType) {
        return hasLeaveTypeCode(leaveType, LeaveManagementService.DEFAULT_AFK_CODE);
    }

    private boolean isNghiLeave(LeaveRequest request) {
        return request != null && hasLeaveTypeCode(request.getLeaveType(), LeaveManagementService.DEFAULT_NGHI_CODE);
    }

    private boolean isWfhLeave(LeaveRequest request) {
        return request != null && isWfhLeave(request.getLeaveType());
    }

    private boolean isWfhLeave(LeaveType leaveType) {
        return hasLeaveTypeCode(leaveType, LeaveManagementService.DEFAULT_WFH_CODE);
    }

    private boolean hasLeaveTypeCode(LeaveType leaveType, String code) {
        return leaveType != null && leaveType.getCode() != null && leaveType.getCode().equalsIgnoreCase(code);
    }

    private String duplicateLeaveMessage(LeaveRequest candidate) {
        if (isAfkLeave(candidate)) {
            return String.format("Khung giờ AFK %s - %s ngày %s đã có yêu cầu AFK/nghỉ trùng thời gian.",
                    candidate.getStartTime(),
                    candidate.getEndTime(),
                    candidate.getStartDate());
        }
        if (candidate.getLeaveType().getUnit() == LeaveTypeUnit.HOUR) {
            return String.format("Khung giờ %s - %s ngày %s đã có yêu cầu nghỉ đang chờ/đã duyệt.",
                    candidate.getStartTime(),
                    candidate.getEndTime(),
                    candidate.getStartDate());
        }
        if (candidate.getTargetShifts() != null && !candidate.getTargetShifts().isEmpty()) {
            String shiftName = candidate.getTargetShifts().stream()
                    .map(Shift::getName)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("đã chọn");
            return String.format("Ca %s ngày %s đã có yêu cầu nghỉ đang chờ/đã duyệt.", shiftName, candidate.getStartDate());
        }
        return String.format("Ngày %s đến %s đã có yêu cầu nghỉ đang chờ/đã duyệt.", candidate.getStartDate(), candidate.getEndDate());
    }

    private void validateNoOverlap(UUID employeeId, LocalDate workDate, Shift newShift) {
        if (!shiftOverlapService.hasCaptureWindow(newShift)) {
            return;
        }

        ShiftOverlapService.CaptureWindow newWindow = shiftOverlapService.captureWindow(workDate, newShift);
        List<EmployeeShift> existingAssignments = employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, workDate);
        for (EmployeeShift existing : existingAssignments) {
            Shift existingShift = existing.getShift();
            if (!shiftOverlapService.hasCaptureWindow(existingShift)) {
                continue;
            }
            ShiftOverlapService.CaptureWindow existingWindow = shiftOverlapService.captureWindow(workDate, existingShift);
            if (shiftOverlapService.overlapsInclusive(newWindow, existingWindow)) {
                throw new RuntimeException(String.format(
                        "Ca làm việc mới %s (%s) bị trùng vùng chấm công với ca %s (%s) của nhân viên ngày %s.",
                        newShift.getName(),
                        shiftOverlapService.format(newWindow),
                        existingShift.getName(),
                        shiftOverlapService.format(existingWindow),
                        workDate
                ));
            }
        }
    }

    private void assertSwapAssignmentsHaveNoAttendanceEvents(ShiftSwapRequest request) {
        EmployeeShift sourceAssignment = resolveAssignment(
                request.getRequester().getId(),
                request.getWorkDate(),
                request.getShift(),
                "ca nguồn"
        );
        assertNoAttendanceEvents(sourceAssignment, "ca nguồn");

        if (request.getType() == SwapRequestType.SWAP) {
            if (request.getTargetEmployee() == null || request.getTargetWorkDate() == null || request.getTargetShift() == null) {
                throw new RuntimeException("Yêu cầu đổi ca thiếu ngày hoặc ca đối ứng.");
            }
            EmployeeShift targetAssignment = resolveAssignment(
                    request.getTargetEmployee().getId(),
                    request.getTargetWorkDate(),
                    request.getTargetShift(),
                    "ca đối ứng"
            );
            assertNoAttendanceEvents(targetAssignment, "ca đối ứng");
        }
    }

    private EmployeeShift resolveAssignment(UUID employeeId, LocalDate workDate, Shift shift, String label) {
        if (employeeId == null || workDate == null || shift == null || shift.getId() == null) {
            throw new RuntimeException("Không xác định được " + label + " trong lịch làm việc.");
        }

        List<EmployeeShift> matches = employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, workDate).stream()
                .filter(assignment -> assignment.getShift() != null)
                .filter(assignment -> Objects.equals(shift.getId(), assignment.getShift().getId()))
                .toList();

        if (matches.isEmpty()) {
            throw new RuntimeException("Ca " + label + " không tồn tại trong lịch của nhân viên ngày " + workDate + ".");
        }
        if (matches.size() > 1) {
            throw new RuntimeException("Không xác định được duy nhất " + label + " trong lịch của nhân viên ngày " + workDate + ".");
        }
        return matches.get(0);
    }

    private void assertNoAttendanceEvents(EmployeeShift assignment, String label) {
        if (attendanceEventRepository.existsByEmployeeShift_Id(assignment.getId())) {
            throw new RuntimeException("Ca " + label + " đã phát sinh dữ liệu chấm công. Vui lòng thực hiện qua luồng điều chỉnh công hoặc điều chỉnh lịch có lý do.");
        }
    }

    @Transactional
    public void cancelLeaveRequest(UUID id) {
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Yêu cầu nghỉ phép không tồn tại."));

        checkLock(request.getStartDate());
        checkLock(request.getEndDate());

        UUID userId = SecurityContextUtils.getCurrentUserId();
        if (request.getEmployee().getUserInfo() == null || !request.getEmployee().getUserInfo().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy yêu cầu này.");
        }

        if (request.getStatus() == ApprovalStatus.PENDING) {
            request.setStatus(ApprovalStatus.CANCELLED);
            leaveRequestRepository.save(request);
        } else if (request.getStatus() == ApprovalStatus.APPROVED) {
            request.setStatus(ApprovalStatus.CANCEL_PENDING);
            leaveRequestRepository.save(request);

            // Notify managers/admins
            List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                    List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
            );
            String title = "Yêu cầu hủy nghỉ phép";
            String content = String.format("Nhân viên %s yêu cầu hủy đơn nghỉ phép từ %s đến %s, đang chờ duyệt.",
                    request.getEmployee().getFullName(), request.getStartDate(), request.getEndDate());
            for (AccountUser mgr : managers) {
                notificationService.createNotification(
                        mgr.getId(), title, content, NotificationType.REQUEST_SUBMITTED, request.getId()
                );
            }
        } else {
            throw new RuntimeException("Không thể hủy yêu cầu ở trạng thái: " + request.getStatus());
        }
    }

    @Transactional
    public void cancelShiftSwapRequest(UUID id) {
        ShiftSwapRequest request = shiftSwapRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Yêu cầu đổi ca không tồn tại."));

        checkLock(request.getWorkDate());
        if (request.getTargetWorkDate() != null) {
            checkLock(request.getTargetWorkDate());
        }

        UUID userId = SecurityContextUtils.getCurrentUserId();
        if (request.getRequester().getUserInfo() == null || !request.getRequester().getUserInfo().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy yêu cầu này.");
        }

        if (request.getStatus() == ApprovalStatus.PENDING) {
            request.setStatus(ApprovalStatus.CANCELLED);
            shiftSwapRequestRepository.save(request);
        } else if (request.getStatus() == ApprovalStatus.APPROVED) {
            request.setStatus(ApprovalStatus.CANCEL_PENDING);
            shiftSwapRequestRepository.save(request);

            // Notify managers/admins
            List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                    List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
            );
            String title = "Yêu cầu hủy đổi ca";
            String content = String.format("Nhân viên %s yêu cầu hủy đơn đổi ca ngày %s, đang chờ duyệt.",
                    request.getRequester().getFullName(), request.getWorkDate());
            for (AccountUser mgr : managers) {
                notificationService.createNotification(
                        mgr.getId(), title, content, NotificationType.REQUEST_SUBMITTED, request.getId()
                );
            }
        } else {
            throw new RuntimeException("Không thể hủy yêu cầu ở trạng thái: " + request.getStatus());
        }
    }

    @Transactional
    public void cancelAttendanceAdjustment(UUID id) {
        AttendanceAdjustment request = attendanceAdjustmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Yêu cầu chỉnh sửa công không tồn tại."));

        checkLock(request.getWorkDate());

        UUID userId = SecurityContextUtils.getCurrentUserId();
        if (request.getEmployee().getUserInfo() == null || !request.getEmployee().getUserInfo().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy yêu cầu này.");
        }

        if (request.getStatus() == ApprovalStatus.PENDING) {
            request.setStatus(ApprovalStatus.CANCELLED);
            attendanceAdjustmentRepository.save(request);
        } else if (request.getStatus() == ApprovalStatus.APPROVED) {
            request.setStatus(ApprovalStatus.CANCEL_PENDING);
            attendanceAdjustmentRepository.save(request);

            // Notify managers/admins
            List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                    List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
            );
            String title = "Yêu cầu hủy chỉnh công";
            String content = String.format("Nhân viên %s yêu cầu hủy đơn chỉnh công ngày %s, đang chờ duyệt.",
                    request.getEmployee().getFullName(), request.getWorkDate());
            for (AccountUser mgr : managers) {
                notificationService.createNotification(
                        mgr.getId(), title, content, NotificationType.REQUEST_SUBMITTED, request.getId()
                );
            }
        } else {
            throw new RuntimeException("Không thể hủy yêu cầu ở trạng thái: " + request.getStatus());
        }
    }
}

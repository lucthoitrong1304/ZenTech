package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final NotificationService notificationService;

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

        request.setStatus(status);
        request.setApprovedBy(user);
        request.setApprovedAt(LocalDateTime.now());
        if (status == ApprovalStatus.REJECTED) {
            request.setRejectionReason(rejectionReason);
        }

        AttendanceAdjustment saved = attendanceAdjustmentRepository.save(request);

        // Notify employee
        if (saved.getEmployee() != null && saved.getEmployee().getUserInfo() != null) {
            UUID employeeAccountId = saved.getEmployee().getUserInfo().getId();
            String title = "Kết quả duyệt chỉnh sửa công";
            String statusStr = saved.getStatus() == ApprovalStatus.APPROVED ? "ĐÃ DUYỆT" : "BỊ TỪ CHỐI";
            String content = String.format("Yêu cầu chỉnh sửa công ngày %s của bạn đã %s.", 
                    saved.getWorkDate(), statusStr);
            if (saved.getStatus() == ApprovalStatus.REJECTED && saved.getRejectionReason() != null) {
                content += " Lý do: " + saved.getRejectionReason();
            }
            notificationService.createNotification(
                    employeeAccountId, 
                    title, 
                    content, 
                    saved.getStatus() == ApprovalStatus.APPROVED ? NotificationType.REQUEST_APPROVED : NotificationType.REQUEST_REJECTED, 
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AttendanceAdjustment> getPendingAttendanceAdjustments() {
        return attendanceAdjustmentRepository.findByStatus(ApprovalStatus.PENDING);
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

        request.setRequester(employee);
        request.setRequestedAt(LocalDateTime.now());
        request.setStatus(ApprovalStatus.PENDING);

        ShiftSwapRequest saved = shiftSwapRequestRepository.save(request);

        Employee reqEmp = employeeRepository.findById(saved.getRequester().getId()).orElse(null);
        Employee tgtEmp = employeeRepository.findById(saved.getTargetEmployee().getId()).orElse(null);

        String reqName = reqEmp != null ? reqEmp.getFullName() : "Nhân viên";
        String tgtName = tgtEmp != null ? tgtEmp.getFullName() : "Nhân viên";

        // 1. Notify target employee
        if (tgtEmp != null && tgtEmp.getUserInfo() != null) {
            String title = "Yêu cầu đổi ca từ đồng nghiệp";
            String content = String.format("Đồng nghiệp %s muốn đổi ca với bạn vào ngày %s.", reqName, saved.getWorkDate());
            notificationService.createNotification(
                    tgtEmp.getUserInfo().getId(),
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

        request.setStatus(status);
        request.setApprovedBy(user);
        request.setApprovedAt(LocalDateTime.now());

        ShiftSwapRequest saved = shiftSwapRequestRepository.save(request);

        Employee reqEmp = employeeRepository.findById(saved.getRequester().getId()).orElse(null);
        Employee tgtEmp = employeeRepository.findById(saved.getTargetEmployee().getId()).orElse(null);

        String title = "Kết quả duyệt đổi ca";
        String statusStr = saved.getStatus() == ApprovalStatus.APPROVED ? "ĐÃ DUYỆT" : "BỊ TỪ CHỐI";
        String content = String.format("Yêu cầu đổi ca ngày %s của bạn đã %s.", saved.getWorkDate(), statusStr);
        NotificationType notiType = saved.getStatus() == ApprovalStatus.APPROVED ? NotificationType.REQUEST_APPROVED : NotificationType.REQUEST_REJECTED;

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

        // 2. Notify targetEmployee if approved
        if (saved.getStatus() == ApprovalStatus.APPROVED && tgtEmp != null && tgtEmp.getUserInfo() != null) {
            String tgtContent = String.format("Lịch làm việc của bạn ngày %s đã thay đổi do yêu cầu đổi ca đã được phê duyệt.", saved.getWorkDate());
            notificationService.createNotification(
                    tgtEmp.getUserInfo().getId(),
                    title,
                    tgtContent,
                    NotificationType.REQUEST_APPROVED,
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> getPendingShiftSwaps() {
        return shiftSwapRequestRepository.findByStatus(ApprovalStatus.PENDING);
    }

    // --- Leave Requests ---
    @Transactional
    public LeaveRequest requestLeave(LeaveRequest request) {
        // Check lock for start date and end date
        checkLock(request.getStartDate());
        checkLock(request.getEndDate());

        UUID userId = SecurityContextUtils.getCurrentUserId();
        Employee employee = userId != null ? employeeRepository.findByUserInfo_Id(userId).orElse(null) : null;

        if (employee == null) {
            throw new RuntimeException("Không tìm thấy thông tin nhân viên.");
        }

        request.setEmployee(employee);
        request.setRequestedAt(LocalDateTime.now());
        request.setStatus(ApprovalStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(request);

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

        UUID userId = SecurityContextUtils.getCurrentUserId();
        AccountUser user = userId != null ? accountUserRepository.findById(userId).orElse(null) : null;

        request.setStatus(status);
        request.setApprovedBy(user);
        request.setApprovedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify employee
        if (saved.getEmployee() != null && saved.getEmployee().getUserInfo() != null) {
            UUID employeeAccountId = saved.getEmployee().getUserInfo().getId();
            String title = "Kết quả duyệt nghỉ phép";
            String statusStr = saved.getStatus() == ApprovalStatus.APPROVED ? "ĐÃ DUYỆT" : "BỊ TỪ CHỐI";
            String content = String.format("Yêu cầu nghỉ phép từ ngày %s đến ngày %s của bạn đã %s.", 
                    saved.getStartDate(), saved.getEndDate(), statusStr);
            notificationService.createNotification(
                    employeeAccountId,
                    title,
                    content,
                    saved.getStatus() == ApprovalStatus.APPROVED ? NotificationType.REQUEST_APPROVED : NotificationType.REQUEST_REJECTED,
                    saved.getId()
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingLeaves() {
        return leaveRequestRepository.findByStatus(ApprovalStatus.PENDING);
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
}

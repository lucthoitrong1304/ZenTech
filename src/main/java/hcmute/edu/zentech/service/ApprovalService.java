package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.security.SecurityContextUtils;
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
        
        return attendanceAdjustmentRepository.save(request);
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

        return attendanceAdjustmentRepository.save(request);
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

        return shiftSwapRequestRepository.save(request);
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

        return shiftSwapRequestRepository.save(request);
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

        return leaveRequestRepository.save(request);
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

        return leaveRequestRepository.save(request);
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

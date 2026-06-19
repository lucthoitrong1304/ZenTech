package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.response.*;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.AttendanceRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.projection.AttendanceRecordProjection;
import hcmute.edu.zentech.repository.projection.AttendanceStatisticsProjection;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.utils.FaceEncryptionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final AccountUserRepository accountUserRepository;
    private final R2StorageService r2StorageService;
    private final ObjectMapper objectMapper;
    private final FaceEncryptionUtils faceEncryptionUtils;
    private final AdminActivityLogService adminActivityLogService;

    @Value("${zentech.attendance.face-match-threshold:0.5}")
    private double faceMatchThreshold;

    private final ConcurrentHashMap<UUID, FailedAttempts> failedAttemptsMap = new ConcurrentHashMap<>();

    private static class FailedAttempts {
        int count;
        long lastAttemptTime;

        FailedAttempts(int count, long lastAttemptTime) {
            this.count = count;
            this.lastAttemptTime = lastAttemptTime;
        }
    }

    @Transactional
    public EmployeeProfileResponse checkIn(CheckInRequest request) {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        // 1. Kiểm tra Rate Limit
        checkCheckInRateLimit(accountId);

        List<Float> inputDescriptor = request.getFaceDescriptor();
        if (inputDescriptor == null || inputDescriptor.size() != 128) {
            throw new IllegalArgumentException("Đặc trưng khuôn mặt không hợp lệ.");
        }

        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));

        if (employee.getFaceDescriptors() == null || employee.getFaceDescriptors().isEmpty()) {
            throw new RuntimeException("Nhân viên chưa đăng ký khuôn mặt.");
        }

        double minDistance = Double.MAX_VALUE;

        try {
            // Giải mã khuôn mặt
            String decryptedDescriptors = faceEncryptionUtils.decrypt(employee.getFaceDescriptors());
            List<List<Float>> registeredDescriptors = objectMapper.readValue(
                    decryptedDescriptors,
                    new TypeReference<List<List<Float>>>() {}
            );

            for (List<Float> registeredDesc : registeredDescriptors) {
                double distance = calculateEuclideanDistance(inputDescriptor, registeredDesc);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi giải mã đặc trưng khuôn mặt.", e);
        }

        if (minDistance > faceMatchThreshold) {
            // Ghi nhận thất bại và tăng đếm rate limit
            recordFailedCheckIn(accountId);

            // Ghi audit log thất bại
            adminActivityLogService.log(
                    accountId,
                    ActivityArea.MANAGEMENT,
                    "EMPLOYEE",
                    ActivityAction.FACE_VERIFICATION_FAILED,
                    ActivitySeverity.WARNING,
                    "Employee",
                    employee.getId().toString(),
                    employee.getFullName(),
                    "Xác thực khuôn mặt thất bại (Khoảng cách: " + String.format("%.4f", minDistance) + ")",
                    null
            );

            throw new RuntimeException("Không nhận diện được khuôn mặt. Vui lòng thử lại.");
        }

        // Thành công: Reset rate limit
        resetFailedCheckIn(accountId);

        // Determine Attendance Status
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        AttendanceStatus status;
        
        LocalTime startWork = LocalTime.of(8, 0);
        LocalTime lateThreshold = LocalTime.of(8, 15);
        
        if (time.isBefore(startWork)) {
            status = AttendanceStatus.EARLY;
        } else if (time.isBefore(lateThreshold)) {
            status = AttendanceStatus.ON_TIME;
        } else {
            status = AttendanceStatus.LATE;
        }

        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setCheckInTime(now);
        attendance.setStatus(status);
        attendanceRepository.save(attendance);

        // Ghi audit log thành công
        adminActivityLogService.log(
                accountId,
                ActivityArea.MANAGEMENT,
                "EMPLOYEE",
                ActivityAction.FACE_VERIFICATION_SUCCESS,
                ActivitySeverity.INFO,
                "Employee",
                employee.getId().toString(),
                employee.getFullName(),
                "Xác thực khuôn mặt thành công. Trạng thái: " + status,
                null
        );

        return mapToResponse(employee);
    }

    private void checkCheckInRateLimit(UUID accountId) {
        FailedAttempts attempts = failedAttemptsMap.get(accountId);
        if (attempts != null) {
            long duration = System.currentTimeMillis() - attempts.lastAttemptTime;
            if (duration < 15 * 60 * 1000) { // Trong vòng 15 phút
                if (attempts.count >= 5) {
                    long minutesLeft = 15 - (duration / (60 * 1000));
                    throw new RuntimeException("Tài khoản của bạn đã bị khóa chức năng điểm danh trong " + minutesLeft + " phút nữa do quét sai mặt quá 5 lần.");
                }
            } else {
                // Đã hết thời gian khóa, reset
                failedAttemptsMap.remove(accountId);
            }
        }
    }

    private void recordFailedCheckIn(UUID accountId) {
        failedAttemptsMap.compute(accountId, (key, value) -> {
            long now = System.currentTimeMillis();
            if (value == null || (now - value.lastAttemptTime > 15 * 60 * 1000)) {
                return new FailedAttempts(1, now);
            } else {
                value.count++;
                value.lastAttemptTime = now;
                return value;
            }
        });
    }

    private void resetFailedCheckIn(UUID accountId) {
        failedAttemptsMap.remove(accountId);
    }

    private double calculateEuclideanDistance(List<Float> desc1, List<Float> desc2) {
        if (desc1.size() != desc2.size()) {
            return Double.MAX_VALUE;
        }
        double sum = 0.0;
        for (int i = 0; i < desc1.size(); i++) {
            double diff = desc1.get(i) - desc2.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
    
    private EmployeeProfileResponse mapToResponse(Employee employee) {
        AccountUser user = employee.getUserInfo();
        String imageUrl = employee.getImageUrl();
        if (imageUrl != null && !imageUrl.startsWith("http")) {
            imageUrl = r2StorageService.getPresignedGetUrl(imageUrl);
        }

        return EmployeeProfileResponse.builder()
                .id(employee.getId())
                .fullName(employee.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .imageUrl(imageUrl)
                .phoneNumber(employee.getPhoneNumber())
                .address(employee.getAddress())
                .dateOfBirth(employee.getDateOfBirth())
                .isActive(user.isActive())
                .build();
    }


    @Transactional(readOnly = true)
    public AttendanceReportResponse getAttendanceReport(LocalDate startDate, LocalDate endDate, int page, int size) {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        AccountUser accountUser = accountUserRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        Pageable pageable = PageRequest.of(page, size);

        Page<AttendanceRecordProjection> records;
        AttendanceStatisticsProjection stats;

        if (accountUser.getRole() == Role.OWNER || accountUser.getRole() == Role.MANAGER || accountUser.getRole() == Role.ADMIN) {
            records = attendanceRepository.findAllRecordsBetweenDates(startDateTime, endDateTime, pageable);
            stats = attendanceRepository.getStatisticsBetweenDates(startDateTime, endDateTime);
        } else if (accountUser.getRole() == Role.EMPLOYEE) {
            Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));
            records = attendanceRepository.findRecordsByEmployeeIdAndDates(employee.getId(), startDateTime, endDateTime, pageable);
            stats = attendanceRepository.getStatisticsByEmployeeIdAndDates(employee.getId(), startDateTime, endDateTime);
        } else {
            throw new RuntimeException("Bạn không có quyền truy cập báo cáo này.");
        }

        List<AttendanceRecordResponse> recordResponses = records.getContent().stream()
                .map(r -> AttendanceRecordResponse.builder()
                        .id(r.getId())
                        .employeeId(r.getEmployeeId())
                        .employeeName(r.getEmployeeName())
                        .checkInTime(r.getCheckInTime())
                        .status(r.getStatus())
                        .build())
                .collect(Collectors.toList());

        PageResponse<AttendanceRecordResponse> pageResponse = PageResponse.from(records, recordResponses);

        AttendanceStatisticsResponse statisticsResponse = AttendanceStatisticsResponse.builder()
                .totalRecords(stats.getTotalRecords())
                .totalOnTime(stats.getTotalOnTime())
                .totalLate(stats.getTotalLate())
                .totalEarly(stats.getTotalEarly())
                .build();

        return AttendanceReportResponse.builder()
                .statistics(statisticsResponse)
                .records(pageResponse)
                .build();
    }
}

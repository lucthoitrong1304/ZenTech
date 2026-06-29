package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.response.*;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.utils.FaceEncryptionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final AccountUserRepository accountUserRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final PayPeriodRepository payPeriodRepository;
    private final AttendanceCalculator attendanceCalculator;
    private final R2StorageService r2StorageService;
    private final ObjectMapper objectMapper;
    private final FaceEncryptionUtils faceEncryptionUtils;
    private final AdminActivityLogService adminActivityLogService;
    private final AttendanceLocationPolicyService attendanceLocationPolicyService;


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

        // Kiểm tra kỳ công có bị khóa không
        Optional<PayPeriod> periodOpt = payPeriodRepository.findPeriodActiveAt(LocalDate.now());
        if (periodOpt.isPresent() && periodOpt.get().isLocked()) {
            throw new RuntimeException("Kỳ công đã bị khóa. Không thể thực hiện chấm công.");
        }

        // 1. Kiểm tra Rate Limit
        checkCheckInRateLimit(accountId);

        List<Float> inputDescriptor = request.getFaceDescriptor();
        if (inputDescriptor == null || inputDescriptor.size() != 128) {
            throw new IllegalArgumentException("Đặc trưng khuôn mặt không hợp lệ.");
        }

        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));

        boolean locationValid = attendanceLocationPolicyService.isLocationAllowed(
                request.getLatitude(),
                request.getLongitude()
        );
        if (!locationValid) {
            String message = attendanceLocationPolicyService.isPolicyEnabled()
                    ? "Vị trí hiện tại nằm ngoài phạm vi check-in hợp lệ."
                    : "Vị trí check-in không hợp lệ.";
            throw new RuntimeException(message);
        }

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

        LocalDateTime now = LocalDateTime.now();
        
        // Xác định loại sự kiện CHECK_IN hay CHECK_OUT dựa trên lịch sử hôm nay
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        List<AttendanceEvent> todayEvents = attendanceEventRepository
                .findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(employee.getId(), startOfDay, endOfDay);
        
        AttendanceEventType type = AttendanceEventType.CHECK_IN;
        if (!todayEvents.isEmpty()) {
            AttendanceEvent lastEvent = todayEvents.get(todayEvents.size() - 1);
            if (lastEvent.getEventType() == AttendanceEventType.CHECK_IN) {
                type = AttendanceEventType.CHECK_OUT;
            }
        }

        AttendanceCalculator.EffectiveShift selectedShift = resolveAttendanceShift(employee, now, type, todayEvents);

        AttendanceEvent event = new AttendanceEvent();
        event.setEmployee(employee);
        event.setEmployeeShift(selectedShift.assignment());
        event.setTimestamp(now);
        event.setEventType(type);
        event.setSource("FACE");
        event.setDetails("Xác thực khuôn mặt thành công. Ca: " + selectedShift.shift().getName()
                + ". (Khoảng cách: " + String.format("%.4f", minDistance) + ")");
        event.setLatitude(request.getLatitude());
        event.setLongitude(request.getLongitude());
        event.setAccuracyMeters(request.getAccuracyMeters());
        event.setLocationValid(locationValid);
        attendanceEventRepository.save(event);

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
                "Xác thực khuôn mặt thành công. Sự kiện: " + type,
                null
        );

        return mapToResponse(employee);
    }

    private AttendanceCalculator.EffectiveShift resolveAttendanceShift(
            Employee employee,
            LocalDateTime timestamp,
            AttendanceEventType nextType,
            List<AttendanceEvent> todayEvents
    ) {
        LocalDate workDate = timestamp.toLocalDate();
        LocalTime now = timestamp.toLocalTime();
        List<AttendanceCalculator.EffectiveShift> shifts = attendanceCalculator.resolveEffectiveShifts(employee.getId(), workDate);

        if (shifts.isEmpty()) {
            throw new RuntimeException("Hôm nay bạn không có ca làm việc.");
        }

        if (nextType == AttendanceEventType.CHECK_OUT) {
            AttendanceEvent openCheckIn = todayEvents.isEmpty() ? null : todayEvents.get(todayEvents.size() - 1);
            if (openCheckIn != null && openCheckIn.getEmployeeShift() != null) {
                UUID openAssignmentId = openCheckIn.getEmployeeShift().getId();
                return shifts.stream()
                        .filter(item -> item.assignment() != null && item.assignment().getId().equals(openAssignmentId))
                        .findFirst()
                        .orElseGet(() -> new AttendanceCalculator.EffectiveShift(openCheckIn.getEmployeeShift(), openCheckIn.getEmployeeShift().getShift()));
            }

            return shifts.stream()
                    .filter(item -> isInCaptureRange(now, item.shift()))
                    .findFirst()
                    .orElse(shifts.get(shifts.size() - 1));
        }

        Set<UUID> checkedInAssignmentIds = todayEvents.stream()
                .filter(event -> event.getEventType() == AttendanceEventType.CHECK_IN)
                .map(AttendanceEvent::getEmployeeShift)
                .filter(Objects::nonNull)
                .map(EmployeeShift::getId)
                .collect(Collectors.toSet());

        AttendanceCalculator.EffectiveShift nextShift = shifts.stream()
                .filter(item -> item.assignment() == null || !checkedInAssignmentIds.contains(item.assignment().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Các ca hôm nay đã được check-in. Vui lòng checkout ca đang mở hoặc kiểm tra lại lịch."));

        Shift shift = nextShift.shift();
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return nextShift;
        }

        LocalTime allowedStart = shift.getStartTime().minusMinutes(defaultInt(shift.getEarlyCheckInMinutes(), 30));
        if (now.isBefore(allowedStart)) {
            throw new RuntimeException("Chưa tới giờ check-in ca " + shift.getName() + ".");
        }
        if (!now.isBefore(shift.getEndTime())) {
            throw new RuntimeException("Ca " + shift.getName() + " đã kết thúc. Vui lòng gửi yêu cầu chỉnh công.");
        }

        return nextShift;
    }

    private boolean isInCaptureRange(LocalTime time, Shift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return true;
        }
        LocalTime start = shift.getStartTime().minusMinutes(defaultInt(shift.getEarlyCheckInMinutes(), 30));
        LocalTime end = shift.getEndTime().plusMinutes(defaultInt(shift.getLateCheckOutMinutes(), 60));
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
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

        List<Employee> targetEmployees = new ArrayList<>();
        boolean isManager = accountUser.getRole() == Role.OWNER || accountUser.getRole() == Role.MANAGER || accountUser.getRole() == Role.ADMIN;

        if (isManager) {
            targetEmployees = employeeRepository.findAll();
        } else if (accountUser.getRole() == Role.EMPLOYEE) {
            Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));
            targetEmployees.add(employee);
        } else {
            throw new RuntimeException("Bạn không có quyền truy cập báo cáo này.");
        }

        List<LocalDate> allDates = new ArrayList<>();
        LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            allDates.add(curr);
            curr = curr.plusDays(1);
        }

        long totalRecords = 0;
        long totalOnTime = 0;
        long totalLate = 0;
        long totalEarly = 0;
        double totalWorkingHours = 0.0;
        long totalMissingCheckIn = 0;
        long totalMissingCheckOut = 0;
        long totalAbsent = 0;
        long totalLeave = 0;

        List<AttendanceRecordResponse> allCalculatedRecords = new ArrayList<>();

        for (Employee emp : targetEmployees) {
            for (LocalDate date : allDates) {
                AttendanceRecordResponse rec = attendanceCalculator.calculateDayAttendance(emp, date);
                boolean isOff = "OFF".equals(rec.getStatus());
                if (!isOff || rec.getCheckInTime() != null || rec.getCheckOutTime() != null) {
                    allCalculatedRecords.add(rec);

                    totalRecords++;
                    totalWorkingHours += rec.getWorkingHours();
                    
                    switch (rec.getStatus()) {
                        case "ON_TIME":
                            totalOnTime++;
                            break;
                        case "LATE":
                            totalLate++;
                            break;
                        case "EARLY_CHECKOUT":
                            totalEarly++;
                            break;
                        case "LATE_AND_EARLY":
                            totalLate++;
                            totalEarly++;
                            break;
                        case "MISSING_CHECK_IN":
                            totalMissingCheckIn++;
                            break;
                        case "MISSING_CHECK_OUT":
                            totalMissingCheckOut++;
                            break;
                        case "ABSENT_UNEXCUSED":
                            totalAbsent++;
                            break;
                        case "ABSENT_EXCUSED":
                            totalLeave++;
                            break;
                    }
                }
            }
        }

        // Sort by work date descending and employee name ascending
        allCalculatedRecords.sort((r1, r2) -> {
            int dateComp = r2.getWorkDate().compareTo(r1.getWorkDate());
            if (dateComp != 0) return dateComp;
            return r1.getEmployeeName().compareTo(r2.getEmployeeName());
        });

        int totalElements = allCalculatedRecords.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<AttendanceRecordResponse> paginatedList = new ArrayList<>();
        if (fromIndex < totalElements) {
            paginatedList = allCalculatedRecords.subList(fromIndex, toIndex);
        }

        Page<AttendanceRecordResponse> pageResult = 
                new PageImpl<>(paginatedList, PageRequest.of(page, size), totalElements);

        PageResponse<AttendanceRecordResponse> pageResponse = PageResponse.from(pageResult, paginatedList);

        AttendanceStatisticsResponse statisticsResponse = AttendanceStatisticsResponse.builder()
                .totalRecords(totalRecords)
                .totalOnTime(totalOnTime)
                .totalLate(totalLate)
                .totalEarly(totalEarly)
                .totalWorkingHours(totalWorkingHours)
                .totalMissingCheckIn(totalMissingCheckIn)
                .totalMissingCheckOut(totalMissingCheckOut)
                .totalAbsent(totalAbsent)
                .totalLeave(totalLeave)
                .build();

        return AttendanceReportResponse.builder()
                .statistics(statisticsResponse)
                .records(pageResponse)
                .build();
    }
}

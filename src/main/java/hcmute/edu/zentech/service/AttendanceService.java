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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceReportPreferenceRepository attendanceReportPreferenceRepository;


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

        if (employee.getFaceDescriptors() == null || employee.getFaceDescriptors().isEmpty()) {
            throw new RuntimeException("Nhân viên chưa đăng ký khuôn mặt.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        List<AttendanceEvent> todayEvents = attendanceEventRepository
                .findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(employee.getId(), startOfDay, endOfDay);
        
        AttendanceAction attendanceAction = resolveAttendanceAction(employee, now, todayEvents, request.getRequestedAction());
        AttendanceEventType type = attendanceAction.type();
        AttendanceCalculator.EffectiveShift selectedShift = attendanceAction.shift();

        if (selectedShift.isLeave()) {
            throw new RuntimeException("Bạn đã được duyệt nghỉ phép ca " + selectedShift.shift().getName() + ".");
        }

        // WFH bypass check
        boolean hasWfhRequest = !leaveRequestRepository.findWfhRequestsForEmployeeOnDate(
                employee.getId(),
                now.toLocalDate(),
                List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED)
        ).isEmpty();

        boolean locationValid = true;
        if (!selectedShift.isWfh() && !hasWfhRequest) {
            locationValid = attendanceLocationPolicyService.isLocationAllowed(
                    request.getLatitude(),
                    request.getLongitude()
            );
            if (!locationValid) {
                String message = attendanceLocationPolicyService.isPolicyEnabled()
                        ? "Vị trí hiện tại nằm ngoài phạm vi check-in hợp lệ."
                        : "Vị trí check-in không hợp lệ.";
                throw new RuntimeException(message);
            }
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

        FaceImageUploadResult faceImageUpload = uploadAttendanceFaceImage(request.getFaceImage(), employee.getId());
        String faceImageKey = faceImageUpload.key();

        AttendanceEvent event = new AttendanceEvent();
        event.setEmployee(employee);
        event.setEmployeeShift(selectedShift.assignment());
        event.setTimestamp(now);
        event.setEventType(type);
        event.setSource("FACE");
        String eventDetails = ((selectedShift.isWfh() || hasWfhRequest) ? "[WFH] " : "")
                + "Xác thực khuôn mặt thành công. Ca: " + selectedShift.shift().getName()
                + ". (Khoảng cách: " + String.format("%.4f", minDistance) + ")";
        if (faceImageUpload.marker() != null) {
            eventDetails += " " + faceImageUpload.marker();
        }
        event.setDetails(eventDetails);
        event.setLatitude(request.getLatitude());
        event.setLongitude(request.getLongitude());
        event.setAccuracyMeters(request.getAccuracyMeters());
        event.setLocationValid(locationValid);
        event.setFaceImageKey(faceImageKey);
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

    private record FaceImageUploadResult(String key, String marker) {}

    private FaceImageUploadResult uploadAttendanceFaceImage(String faceImage, UUID employeeId) {
        if (faceImage == null || faceImage.isBlank()) {
            log.warn("Face check-in image missing for employee {}", employeeId);
            return new FaceImageUploadResult(null, "[FACE_IMAGE_MISSING]");
        }

        if ("data:,".equals(faceImage) || !faceImage.startsWith("data:image/jpeg;base64,")) {
            log.warn("Invalid face check-in image payload for employee {}: prefix/format is invalid", employeeId);
            return new FaceImageUploadResult(null, "[FACE_IMAGE_INVALID]");
        }

        String base64Image = faceImage.substring("data:image/jpeg;base64,".length()).trim();
        if (base64Image.isBlank()) {
            log.warn("Invalid face check-in image payload for employee {}: base64 content is blank", employeeId);
            return new FaceImageUploadResult(null, "[FACE_IMAGE_INVALID]");
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Image);
            if (decodedBytes.length == 0) {
                log.warn("Invalid face check-in image payload for employee {}: decoded image is empty", employeeId);
                return new FaceImageUploadResult(null, "[FACE_IMAGE_INVALID]");
            }

            String uniqueFilename = "checkin-" + UUID.randomUUID() + ".jpg";
            String faceImageKey = "uploads/attendance-faces/" + employeeId + "/" + uniqueFilename;
            r2StorageService.uploadFileBytes(faceImageKey, decodedBytes, "image/jpeg");
            return new FaceImageUploadResult(faceImageKey, null);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid base64 face check-in image for employee {}: {}", employeeId, e.getMessage());
            return new FaceImageUploadResult(null, "[FACE_IMAGE_INVALID]");
        } catch (Exception e) {
            log.error("Failed to upload face check-in image to R2 for employee {}: {}", employeeId, e.getMessage(), e);
            return new FaceImageUploadResult(null, "[FACE_IMAGE_UPLOAD_FAILED]");
        }
    }

    private record AttendanceAction(AttendanceEventType type, AttendanceCalculator.EffectiveShift shift) {}

    private AttendanceAction resolveAttendanceAction(
            Employee employee,
            LocalDateTime timestamp,
            List<AttendanceEvent> todayEvents,
            AttendanceEventType requestedAction
    ) {
        if (!todayEvents.isEmpty()) {
            AttendanceEvent lastEvent = todayEvents.stream().max(java.util.Comparator.comparing(AttendanceEvent::getTimestamp)).orElse(null);
            if (lastEvent != null && java.time.Duration.between(lastEvent.getTimestamp(), timestamp).abs().toSeconds() <= 10) {
                throw new RuntimeException("Bạn thao tác quá nhanh. Vui lòng thử lại sau vài giây.");
            }
        }

        LocalDate workDate = timestamp.toLocalDate();
        LocalTime now = timestamp.toLocalTime();
        List<AttendanceCalculator.EffectiveShift> shifts = attendanceCalculator.resolveEffectiveShifts(employee.getId(), workDate);

        if (shifts.isEmpty()) {
            throw new RuntimeException("Hôm nay bạn không có ca làm việc.");
        }

        Optional<AttendanceCalculator.EffectiveShift> currentShiftOpt = shifts.stream()
                .filter(item -> isInCaptureRange(now, item.shift()))
                .findFirst();

        if (currentShiftOpt.isEmpty()) {
            throw new RuntimeException("Hiện tại không nằm trong thời gian điểm danh của bất kỳ ca nào.");
        }

        AttendanceCalculator.EffectiveShift currentShift = currentShiftOpt.get();

        List<AttendanceEvent> shiftEvents = todayEvents.stream()
                .filter(e -> {
                    if (currentShift.assignment() != null && e.getEmployeeShift() != null) {
                        return currentShift.assignment().getId().equals(e.getEmployeeShift().getId());
                    }
                    if (currentShift.assignment() == null && e.getEmployeeShift() == null) {
                        return isInCaptureRange(e.getTimestamp().toLocalTime(), currentShift.shift());
                    }
                    return false;
                })
                .sorted(java.util.Comparator.comparing(AttendanceEvent::getTimestamp))
                .toList();

        AttendanceEventType expected = AttendanceEventType.CHECK_IN;
        for (AttendanceEvent event : shiftEvents) {
            if (event.getEventType() != expected) {
                throw new RuntimeException("Dữ liệu chấm công của ca đang sai thứ tự. Vui lòng liên hệ quản lý để điều chỉnh.");
            }
            expected = expected == AttendanceEventType.CHECK_IN ? AttendanceEventType.CHECK_OUT : AttendanceEventType.CHECK_IN;
        }

        // Tự động xác định hành động tiếp theo là CHECK_IN hay CHECK_OUT dựa trên lịch sử ca
        AttendanceEventType actualAction = expected;
        return new AttendanceAction(actualAction, currentShift);
    }

    private boolean isInCaptureRange(LocalTime time, Shift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return true;
        }
        LocalTime start = shift.getStartTime().minusMinutes(defaultInt(shift.getEarlyCheckInMinutes(), 30));
        LocalTime end = shift.getEndTime().plusMinutes(defaultInt(shift.getLateCheckOutMinutes(), 60));
        if (!start.isAfter(end)) {
            return !time.isBefore(start) && !time.isAfter(end);
        } else {
            return !time.isBefore(start) || !time.isAfter(end);
        }
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

        List<Employee> targetEmployees = employeeRepository.findAll();

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
                    
                    String statusKey = rec.getStatus();
                    if (statusKey != null && statusKey.startsWith("WFH_")) {
                        statusKey = statusKey.substring(4);
                    }
                    switch (statusKey) {
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
                        case "EARLY_AND_EARLY":
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

        NavigableMap<LocalDate, List<AttendanceRecordResponse>> recordsByDate = allCalculatedRecords.stream()
                .collect(Collectors.groupingBy(AttendanceRecordResponse::getWorkDate, TreeMap::new, Collectors.toList()));
        List<AttendanceDayGroupResponse> allDays = recordsByDate.descendingMap().entrySet().stream()
                .map(entry -> AttendanceDayGroupResponse.builder()
                        .workDate(entry.getKey()).records(entry.getValue())
                        .summary(buildDaySummary(entry.getValue())).build())
                .toList();
        int totalElements = allDays.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<AttendanceDayGroupResponse> paginatedList = allDays.subList(fromIndex, toIndex);
        Page<AttendanceDayGroupResponse> pageResult = new PageImpl<>(paginatedList, PageRequest.of(page, size), totalElements);
        PageResponse<AttendanceDayGroupResponse> pageResponse = PageResponse.from(pageResult, paginatedList);

        List<AttendanceShiftBreakdownResponse> allShifts = allCalculatedRecords.stream()
                .flatMap(record -> record.getShiftBreakdowns().stream()).toList();
        AttendanceStatisticsResponse statisticsResponse = AttendanceStatisticsResponse.builder()
                .totalRecords(allShifts.size()).totalEmployees(allCalculatedRecords.stream().map(AttendanceRecordResponse::getEmployeeId).distinct().count())
                .totalShifts(allShifts.size())
                .totalOnTime(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isOnTime).count())
                .earlyArrival(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isEarlyArrival).count())
                .totalLate(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isLate).count())
                .totalEarly(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isEarlyCheckout).count())
                .earlyCheckout(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isEarlyCheckout).count())
                .workFromHome(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isWfh).count())
                .notStarted(allShifts.stream().filter(s -> "NOT_STARTED".equals(s.getStatus())).count())
                .provisional(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isProvisional).count())
                .totalWorkingHours(allShifts.stream().mapToDouble(AttendanceShiftBreakdownResponse::getWorkingHours).sum())
                .totalMissingCheckIn(allShifts.stream().filter(s -> "MISSING_CHECK_IN".equals(s.getStatus())).count())
                .totalMissingCheckOut(allShifts.stream().filter(s -> s.getStatus().endsWith("MISSING_CHECK_OUT")).count())
                .totalAbsent(allShifts.stream().filter(s -> "ABSENT_UNEXCUSED".equals(s.getStatus())).count())
                .totalLeave(allShifts.stream().filter(AttendanceShiftBreakdownResponse::isLeave).count())
                .build();

        return AttendanceReportResponse.builder()
                .statistics(statisticsResponse)
                .days(pageResponse)
                .build();
    }

    private static final List<String> DEFAULT_REPORT_METRICS = List.of("onTime", "earlyArrival", "late", "earlyCheckout", "leave", "workFromHome", "absent", "missingCheckIn", "missingCheckOut", "notStarted");

    @Transactional(readOnly = true)
    public AttendanceReportPreferenceResponse getReportPreference() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        return attendanceReportPreferenceRepository.findById(accountId)
                .map(p -> AttendanceReportPreferenceResponse.builder().visibleMetrics(Arrays.asList(p.getVisibleMetrics().split(","))).updatedAt(p.getUpdatedAt()).build())
                .orElseGet(() -> AttendanceReportPreferenceResponse.builder().visibleMetrics(DEFAULT_REPORT_METRICS).build());
    }

    @Transactional
    public AttendanceReportPreferenceResponse saveReportPreference(List<String> metrics) {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        List<String> normalized = metrics == null ? List.of() : metrics.stream().filter(DEFAULT_REPORT_METRICS::contains).distinct().toList();
        if (normalized.isEmpty()) throw new RuntimeException("Cần hiển thị tối thiểu một chỉ số.");
        AttendanceReportPreference preference = new AttendanceReportPreference(accountId, String.join(",", normalized), LocalDateTime.now());
        attendanceReportPreferenceRepository.save(preference);
        return AttendanceReportPreferenceResponse.builder().visibleMetrics(normalized).updatedAt(preference.getUpdatedAt()).build();
    }

    private AttendanceDaySummaryResponse buildDaySummary(List<AttendanceRecordResponse> records) {
        Set<String> scheduledShiftKeys = new HashSet<>();
        long onTime = 0, earlyArrival = 0, late = 0, earlyCheckout = 0, leave = 0, wfh = 0, absent = 0, missingIn = 0, missingOut = 0, notStarted = 0, provisional = 0;
        double hours = 0;
        for (AttendanceRecordResponse record : records) for (AttendanceShiftBreakdownResponse shift : record.getShiftBreakdowns()) {
            scheduledShiftKeys.add(shift.getShiftId() != null ? shift.getShiftId().toString() : shift.getShiftName());
            hours += shift.getWorkingHours();
            if (shift.isOnTime()) onTime++; if (shift.isEarlyArrival()) earlyArrival++; if (shift.isLate()) late++; if (shift.isEarlyCheckout()) earlyCheckout++;
            if (shift.isLeave()) leave++; if (shift.isWfh()) wfh++; if (shift.isProvisional()) provisional++;
            String status = shift.getStatus();
            if ("ABSENT_UNEXCUSED".equals(status)) absent++;
            if ("MISSING_CHECK_IN".equals(status)) missingIn++;
            if (status.endsWith("MISSING_CHECK_OUT")) missingOut++;
            if ("NOT_STARTED".equals(status)) notStarted++;
        }
        return AttendanceDaySummaryResponse.builder().totalEmployees(records.size()).totalShifts(scheduledShiftKeys.size()).onTime(onTime)
                .earlyArrival(earlyArrival).late(late).earlyCheckout(earlyCheckout).leave(leave).workFromHome(wfh)
                .absent(absent).missingCheckIn(missingIn).missingCheckOut(missingOut).notStarted(notStarted)
                .provisional(provisional).totalWorkingHours(hours).build();
    }
}

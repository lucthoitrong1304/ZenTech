package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.response.*;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Attendance;
import hcmute.edu.zentech.model.AttendanceStatus;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.AttendanceRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.projection.AttendanceRecordProjection;
import hcmute.edu.zentech.repository.projection.AttendanceStatisticsProjection;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final AccountUserRepository accountUserRepository;
    private final R2StorageService r2StorageService;
    private final ObjectMapper objectMapper;

    private static final double FACE_MATCH_THRESHOLD = 0.5;

    @Transactional
    public EmployeeProfileResponse checkIn(CheckInRequest request) {
        List<Float> inputDescriptor = request.getFaceDescriptor();
        if (inputDescriptor == null || inputDescriptor.size() != 128) {
            throw new IllegalArgumentException("Đặc trưng khuôn mặt không hợp lệ.");
        }

        List<Employee> allEmployees = employeeRepository.findAll();
        
        Employee matchedEmployee = null;
        double minDistance = Double.MAX_VALUE;

        for (Employee employee : allEmployees) {
            if (employee.getFaceDescriptors() != null && !employee.getFaceDescriptors().isEmpty()) {
                try {
                    List<List<Float>> registeredDescriptors = objectMapper.readValue(
                            employee.getFaceDescriptors(),
                            new TypeReference<List<List<Float>>>() {}
                    );

                    for (List<Float> registeredDesc : registeredDescriptors) {
                        double distance = calculateEuclideanDistance(inputDescriptor, registeredDesc);
                        if (distance < minDistance) {
                            minDistance = distance;
                            matchedEmployee = employee;
                        }
                    }
                } catch (JsonProcessingException e) {
                    // Log error or ignore invalid JSON
                }
            }
        }

        if (matchedEmployee == null || minDistance > FACE_MATCH_THRESHOLD) {
            throw new RuntimeException("Không nhận diện được khuôn mặt. Vui lòng thử lại.");
        }

        // Determine Attendance Status
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        AttendanceStatus status;
        
        // Example logic:
        // Before 8:00 -> EARLY
        // 8:00 - 8:15 -> ON_TIME
        // After 8:15 -> LATE
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
        attendance.setEmployee(matchedEmployee);
        attendance.setCheckInTime(now);
        attendance.setStatus(status);
        attendanceRepository.save(attendance);

        return mapToResponse(matchedEmployee);
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

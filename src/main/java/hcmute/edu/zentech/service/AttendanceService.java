package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.response.EmployeeProfileResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Attendance;
import hcmute.edu.zentech.model.AttendanceStatus;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.repository.AttendanceRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceService {
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
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
}

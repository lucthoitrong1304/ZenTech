package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.FaceRegistrationRequest;
import hcmute.edu.zentech.dto.request.EmployeeProfileUpdateRequest;
import hcmute.edu.zentech.dto.response.EmployeeProfileResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.utils.FaceEncryptionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeSelfService {
    private final EmployeeRepository employeeRepository;
    private final AccountUserRepository accountUserRepository;
    private final R2StorageService r2StorageService;
    private final ObjectMapper objectMapper;
    private final FaceEncryptionUtils faceEncryptionUtils;
    private final AdminActivityLogService adminActivityLogService;

    public EmployeeProfileResponse getMyProfile() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));
        
        return mapToResponse(employee);
    }

    @Transactional
    public EmployeeProfileResponse updateMyProfile(EmployeeProfileUpdateRequest request) {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));

        employee.setFullName(request.getFullName());
        employee.setPhoneNumber(request.getPhoneNumber());
        employee.setAddress(request.getAddress());
        employee.setDateOfBirth(request.getDateOfBirth());
        if (request.getImageUrl() != null) {
            employee.setImageUrl(request.getImageUrl());
        }

        employee = employeeRepository.save(employee);
        return mapToResponse(employee);
    }

    @Transactional
    public void registerFace(FaceRegistrationRequest request) {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));

        List<List<Float>> descriptors = request.getFaceDescriptors();
        if (descriptors == null || descriptors.size() != 7) {
            throw new IllegalArgumentException("Đăng ký khuôn mặt yêu cầu chính xác 7 góc mặt.");
        }

        for (int i = 0; i < descriptors.size(); i++) {
            List<Float> descriptor = descriptors.get(i);
            if (descriptor == null || descriptor.size() != 128) {
                throw new IllegalArgumentException("Mẫu khuôn mặt thứ " + (i + 1) + " không hợp lệ.");
            }
            for (Float val : descriptor) {
                if (val == null || Float.isNaN(val)) {
                    throw new IllegalArgumentException("Mẫu khuôn mặt chứa giá trị không hợp lệ.");
                }
            }
        }

        try {
            String jsonDescriptors = objectMapper.writeValueAsString(descriptors);
            String encryptedDescriptors = faceEncryptionUtils.encrypt(jsonDescriptors);
            employee.setFaceDescriptors(encryptedDescriptors);
            employeeRepository.save(employee);

            // Ghi audit log
            adminActivityLogService.log(
                    accountId,
                    ActivityArea.MANAGEMENT,
                    "EMPLOYEE",
                    ActivityAction.REGISTER_FACE,
                    ActivitySeverity.INFO,
                    "Employee",
                    employee.getId().toString(),
                    employee.getFullName(),
                    "Đăng ký đặc trưng khuôn mặt thành công",
                    null
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi khi lưu trữ đặc trưng khuôn mặt.", e);
        }
    }

    @Transactional
    public void deleteFace() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));

        employee.setFaceDescriptors(null);
        employeeRepository.save(employee);

        // Ghi audit log
        adminActivityLogService.log(
                accountId,
                ActivityArea.MANAGEMENT,
                "EMPLOYEE",
                ActivityAction.DELETE_FACE,
                ActivitySeverity.INFO,
                "Employee",
                employee.getId().toString(),
                employee.getFullName(),
                "Xóa đặc trưng khuôn mặt thành công",
                null
        );
    }

    private EmployeeProfileResponse mapToResponse(Employee employee) {
        AccountUser user = employee.getUserInfo();
        String imageUrl = employee.getImageUrl();
        if (imageUrl != null && !imageUrl.startsWith("http")) {
            imageUrl = r2StorageService.getPresignedGetUrl(imageUrl);
        }

        boolean hasRegisteredFace = employee.getFaceDescriptors() != null && !employee.getFaceDescriptors().isEmpty();

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
                .hasRegisteredFace(hasRegisteredFace)
                .build();
    }
}

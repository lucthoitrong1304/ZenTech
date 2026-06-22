package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.EmployeeSummaryResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeManagementMapper {
    private final R2StorageService r2StorageService;

    public EmployeeSummaryResponse toEmployeeSummaryResponse(Employee employee) {
        AccountUser account = employee.getUserInfo();

        return EmployeeSummaryResponse.builder()
                .employeeId(employee.getId())
                .accountId(account.getId())
                .email(account.getEmail())
                .fullName(employee.getFullName())
                .imageUrl(resolveImageUrl(employee.getImageUrl()))
                .role(account.getRole())
                .active(account.isActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }
}

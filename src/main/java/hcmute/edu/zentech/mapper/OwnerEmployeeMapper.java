package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.EmployeeSummaryResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Employee;
import org.springframework.stereotype.Component;

@Component
public class OwnerEmployeeMapper {

    public EmployeeSummaryResponse toEmployeeSummaryResponse(Employee employee) {
        AccountUser account = employee.getUserInfo();

        return EmployeeSummaryResponse.builder()
                .employeeId(employee.getId())
                .accountId(account.getId())
                .email(account.getEmail())
                .fullName(employee.getFullName())
                .imageUrl(employee.getImageUrl())
                .role(account.getRole())
                .active(account.isActive())
                .createdAt(account.getCreatedAt())
                .build();
    }
}

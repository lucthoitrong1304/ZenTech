package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.EmployeeCreateRequest;
import hcmute.edu.zentech.dto.response.EmployeeSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.mapper.EmployeeManagementMapper;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.PasswordResetToken;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeManagementService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_EMPLOYEE_SORT = "createdAt,desc";

    private final EmployeeRepository employeeRepository;
    private final AccountUserRepository accountUserRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmployeeManagementMapper employeeManagementMapper;
    private final LeaveManagementService leaveManagementService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public PageResponse<EmployeeSummaryResponse> getEmployees(int page, int size, String sort, String keyword, Boolean active, Role role) {
        validateEmployeeRoleFilter(role);

        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                buildEmployeeSort(sort)
        );

        Page<Employee> employeePage = employeeRepository.searchEmployees(normalizeKeyword(keyword), active, role, pageable);

        return PageResponse.from(
                employeePage,
                employeePage.getContent().stream()
                        .map(employeeManagementMapper::toEmployeeSummaryResponse)
                        .toList()
        );
    }

    @Transactional
    public EmployeeSummaryResponse createEmployee(EmployeeCreateRequest request) {
        validateEmployeeRole(request.getRole());

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (accountUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new RuntimeException("Email đã được sử dụng!");
        }

        AccountUser account = AccountUser.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode("EMPLOYEE_INVITE_" + UUID.randomUUID()))
                .role(request.getRole())
                .isActive(false)
                .createdAt(Instant.now())
                .build();
        account = accountUserRepository.save(account);

        Employee employee = new Employee();
        employee.setFullName(request.getFullName().trim());
        employee.setImageUrl(normalizeNullableText(request.getImageUrl()));
        employee.setUserInfo(account);
        employee = employeeRepository.save(employee);
        leaveManagementService.ensureQuotas(employee, LocalDate.now().getYear());

        String resetTokenString = UUID.randomUUID().toString();
        resetTokenRepository.deleteByUser(account);
        resetTokenRepository.save(PasswordResetToken.builder()
                .token(resetTokenString)
                .user(account)
                .expiryDate(Instant.now().plus(Duration.ofMinutes(10)))
                .build());

        emailService.sendResetPasswordEmail(account.getEmail(), frontendUrl + "/reset-password?token=" + resetTokenString);

        return employeeManagementMapper.toEmployeeSummaryResponse(employee);
    }

    private void validateEmployeeRole(Role role) {
        if (role != Role.EMPLOYEE && role != Role.MANAGER) {
            throw new RuntimeException("Vai trò nhân viên chỉ được là EMPLOYEE hoặc MANAGER");
        }
    }

    private void validateEmployeeRoleFilter(Role role) {
        if (role != null) {
            validateEmployeeRole(role);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    private Sort buildEmployeeSort(String sort) {
        String sortValue = (sort == null || sort.isBlank()) ? DEFAULT_EMPLOYEE_SORT : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "asc";

        Map<String, String> sortableFields = Map.of(
                "createdAt", "userInfo.createdAt",
                "fullName", "fullName",
                "email", "userInfo.email",
                "role", "userInfo.role"
        );

        String defaultField = DEFAULT_EMPLOYEE_SORT.split(",", 2)[0];
        String mappedField = sortableFields.getOrDefault(requestedField, sortableFields.get(defaultField));
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(new Sort.Order(direction, mappedField));
    }
}

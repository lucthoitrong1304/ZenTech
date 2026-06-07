package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CreateInternalAccountRequest;
import hcmute.edu.zentech.dto.response.AccountSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import java.util.Optional;
import hcmute.edu.zentech.repository.projection.AccountSummaryProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAccountService {

    private final AccountUserRepository accountUserRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final R2StorageService r2StorageService;

    public PageResponse<AccountSummaryResponse> getAccounts(
            int page,
            int size,
            String sort,
            String keyword,
            Role role,
            Boolean active
    ) {
        String[] sortParams = sort.split(",");
        Sort.Direction direction = Sort.Direction.fromString(sortParams[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

        Page<AccountSummaryProjection> accountPage = accountUserRepository.findAccountsByFilter(keyword, role, active, pageable);

        return PageResponse.<AccountSummaryResponse>builder()
                .content(accountPage.getContent().stream()
                        .map(this::mapToAccountSummaryResponse)
                        .toList())
                .page(accountPage.getNumber())
                .size(accountPage.getSize())
                .totalElements(accountPage.getTotalElements())
                .totalPages(accountPage.getTotalPages())
                .last(accountPage.isLast())
                .build();
    }

    @Transactional
    public void updateAccountRole(UUID accountId, Role newRole) {
        AccountUser account = accountUserRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

        account.setRole(newRole);
        accountUserRepository.save(account);

        if (newRole == Role.ADMIN || newRole == Role.OWNER || newRole == Role.MANAGER || newRole == Role.EMPLOYEE) {
            boolean employeeExists = employeeRepository.findByUserInfo_Id(accountId).isPresent();
            if (!employeeExists) {
                Employee newEmployee = new Employee();
                newEmployee.setUserInfo(account);
                
                String fullName = "Chưa cập nhật tên";
                String imageUrl = null;
                Optional<Customer> customerOpt = customerRepository.findByUserInfo_Id(accountId);
                if (customerOpt.isPresent()) {
                    Customer customer = customerOpt.get();
                    if (customer.getFullName() != null && !customer.getFullName().isBlank()) {
                        fullName = customer.getFullName();
                    }
                    imageUrl = customer.getImageUrl();
                }
                
                newEmployee.setFullName(fullName);
                newEmployee.setImageUrl(imageUrl);
                employeeRepository.save(newEmployee);
                log.info("Tự động tạo hồ sơ Employee cho tài khoản: {}", account.getEmail());
            }
        } else if (newRole == Role.CUSTOMER) {
            boolean customerExists = customerRepository.findByUserInfo_Id(accountId).isPresent();
            if (!customerExists) {
                Customer newCustomer = new Customer();
                newCustomer.setUserInfo(account);
                
                String fullName = "Chưa cập nhật tên";
                String imageUrl = null;
                Optional<Employee> employeeOpt = employeeRepository.findByUserInfo_Id(accountId);
                if (employeeOpt.isPresent()) {
                    Employee employee = employeeOpt.get();
                    if (employee.getFullName() != null && !employee.getFullName().isBlank()) {
                        fullName = employee.getFullName();
                    }
                    imageUrl = employee.getImageUrl();
                }
                
                newCustomer.setFullName(fullName);
                newCustomer.setImageUrl(imageUrl);
                customerRepository.save(newCustomer);
                log.info("Tự động tạo hồ sơ Customer cho tài khoản: {}", account.getEmail());
            }
        }
    }

    @Transactional
    public void updateAccountStatus(UUID accountId, Boolean isActive) {
        AccountUser account = accountUserRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

        account.setActive(isActive);
        accountUserRepository.save(account);
    }

    @Transactional
    public void createInternalAccount(CreateInternalAccountRequest request) {
        if (request.getRole() == Role.CUSTOMER) {
            throw new RuntimeException("API này chỉ dùng để tạo tài khoản nội bộ.");
        }

        if (accountUserRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng trong hệ thống.");
        }

        AccountUser account = AccountUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .isActive(true)
                .isPasswordSet(true)
                .createdAt(Instant.now())
                .build();

        account = accountUserRepository.save(account);

        Employee employee = new Employee();
        employee.setUserInfo(account);
        employee.setFullName(request.getFullName());
        employeeRepository.save(employee);

        log.info("Đã tạo tài khoản nội bộ mới: {} với Role: {}", request.getEmail(), request.getRole());
    }

    private AccountSummaryResponse mapToAccountSummaryResponse(AccountSummaryProjection projection) {
        return AccountSummaryResponse.builder()
                .id(projection.getId())
                .email(projection.getEmail())
                .role(projection.getRole())
                .isActive(projection.getIsActive())
                .createdAt(projection.getCreatedAt())
                .displayName(projection.getDisplayName())
                .imageUrl(resolveImageUrl(projection.getImageUrl()))
                .build();
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }

        return r2StorageService.getPresignedGetUrl(imageUrl);
    }
}

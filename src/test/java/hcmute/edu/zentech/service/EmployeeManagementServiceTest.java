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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeManagementServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private EmployeeManagementService employeeManagementService;

    @BeforeEach
    void setUp() {
        employeeManagementService = new EmployeeManagementService(
                employeeRepository,
                accountUserRepository,
                resetTokenRepository,
                passwordEncoder,
                emailService,
                new EmployeeManagementMapper()
        );
        ReflectionTestUtils.setField(employeeManagementService, "frontendUrl", "http://localhost:4200");
    }

    @Test
    void createEmployeeSavesInactiveAccountEmployeeTokenAndSendsEmail() {
        EmployeeCreateRequest request = createRequest(" Employee@Example.com ", " Employee One ", Role.EMPLOYEE);

        when(accountUserRepository.existsByEmailIgnoreCase("employee@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(accountUserRepository.save(any(AccountUser.class))).thenAnswer(invocation -> {
            AccountUser account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(UUID.randomUUID());
            return employee;
        });

        EmployeeSummaryResponse response = employeeManagementService.createEmployee(request);

        assertThat(response.getEmployeeId()).isNotNull();
        assertThat(response.getAccountId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("employee@example.com");
        assertThat(response.getFullName()).isEqualTo("Employee One");
        assertThat(response.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(response.isActive()).isFalse();

        ArgumentCaptor<AccountUser> accountCaptor = ArgumentCaptor.forClass(AccountUser.class);
        verify(accountUserRepository).save(accountCaptor.capture());
        AccountUser savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo("employee@example.com");
        assertThat(savedAccount.getPassword()).isEqualTo("encoded-password");
        assertThat(savedAccount.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(savedAccount.isActive()).isFalse();
        assertThat(savedAccount.getCreatedAt()).isNotNull();

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).deleteByUser(savedAccount);
        verify(resetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUser()).isSameAs(savedAccount);
        assertThat(tokenCaptor.getValue().getToken()).isNotBlank();
        assertThat(tokenCaptor.getValue().getExpiryDate()).isAfter(Instant.now());

        verify(emailService).sendResetPasswordEmail(eq("employee@example.com"), anyString());
    }

    @Test
    void createEmployeeThrowsWhenEmailExists() {
        EmployeeCreateRequest request = createRequest("employee@example.com", "Employee One", Role.EMPLOYEE);
        when(accountUserRepository.existsByEmailIgnoreCase("employee@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeManagementService.createEmployee(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email");

        verify(accountUserRepository, never()).save(any(AccountUser.class));
        verify(employeeRepository, never()).save(any(Employee.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString());
    }

    @Test
    void createEmployeeRejectsNonEmployeeRoles() {
        EmployeeCreateRequest request = createRequest("admin@example.com", "Admin One", Role.ADMIN);

        assertThatThrownBy(() -> employeeManagementService.createEmployee(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("EMPLOYEE");

        verify(accountUserRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    void getEmployeesReturnsPagedResponsesAndUsesRequestedFilters() {
        Employee employee = createEmployee("Alice Nguyen", "alice@example.com", true, Role.MANAGER);
        PageRequest pageRequest = PageRequest.of(0, 10);

        when(employeeRepository.searchEmployees(eq("alice"), eq(true), eq(Role.MANAGER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee), pageRequest, 1));

        PageResponse<EmployeeSummaryResponse> response = employeeManagementService.getEmployees(
                0,
                10,
                "email,asc",
                "  alice  ",
                true,
                Role.MANAGER
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getContent().get(0).getRole()).isEqualTo(Role.MANAGER);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeRepository).searchEmployees(eq("alice"), eq(true), eq(Role.MANAGER), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userInfo.email")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userInfo.email").isAscending()).isTrue();
    }

    @Test
    void getEmployeesFallsBackToDefaultSortWhenSortFieldIsInvalid() {
        when(employeeRepository.searchEmployees(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        employeeManagementService.getEmployees(-1, 0, "unknown,desc", null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeRepository).searchEmployees(eq(null), eq(null), eq(null), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userInfo.createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userInfo.createdAt").isDescending()).isTrue();
    }

    private EmployeeCreateRequest createRequest(String email, String fullName, Role role) {
        EmployeeCreateRequest request = new EmployeeCreateRequest();
        request.setEmail(email);
        request.setFullName(fullName);
        request.setImageUrl("https://example.com/avatar.png");
        request.setRole(role);
        return request;
    }

    private Employee createEmployee(String fullName, String email, boolean active, Role role) {
        AccountUser account = AccountUser.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("secret")
                .role(role)
                .isActive(active)
                .createdAt(Instant.parse("2026-04-30T00:00:00Z"))
                .build();

        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFullName(fullName);
        employee.setImageUrl("https://example.com/avatar.png");
        employee.setUserInfo(account);
        return employee;
    }
}

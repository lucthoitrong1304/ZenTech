package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Order(1)
@RequiredArgsConstructor
public class AccountDataInitializer implements CommandLineRunner {
    private static final String DEFAULT_PASSWORD = "Admin@123";

    private final AccountUserRepository accountUserRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        createAccountWithProfileIfMissing("admin@zentech.local", Role.ADMIN, "ZenTech Admin");
        createAccountWithProfileIfMissing("owner@zentech.local", Role.OWNER, "ZenTech Owner");
    }

    private void createAccountWithProfileIfMissing(String email, Role role, String fullName) {
        AccountUser account = accountUserRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createAccount(email, role));

        createEmployeeProfileIfMissing(account, fullName);
    }

    private AccountUser createAccount(String email, Role role) {
        AccountUser account = AccountUser.builder()
                .email(email)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(role)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        return accountUserRepository.save(account);
    }

    private void createEmployeeProfileIfMissing(AccountUser account, String fullName) {
        if (employeeRepository.findByUserInfo_Id(account.getId()).isPresent()) {
            return;
        }

        Employee employee = new Employee();
        employee.setFullName(fullName);
        employee.setImageUrl(null);
        employee.setUserInfo(account);
        employeeRepository.save(employee);
    }
}

package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.RolePermission;
import hcmute.edu.zentech.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RolePermissionInitializer implements ApplicationRunner {
    private static final List<Role> CONFIGURABLE_ROLES = List.of(Role.OWNER, Role.MANAGER, Role.EMPLOYEE);

    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Role role : CONFIGURABLE_ROLES) {
            if (rolePermissionRepository.countByRole(role) > 0) {
                continue;
            }
            List<RolePermission> defaults = Arrays.stream(PermissionCode.values())
                    .map(permission -> RolePermission.builder()
                            .role(role)
                            .permissionCode(permission)
                            .build())
                    .toList();
            rolePermissionRepository.saveAll(defaults);
        }
    }
}

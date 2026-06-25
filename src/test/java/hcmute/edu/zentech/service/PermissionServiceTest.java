package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.RolePermission;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.RolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @Mock
    private AdminActivityLogService activityLogService;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(
                rolePermissionRepository,
                accountUserRepository,
                activityLogService,
                new ObjectMapper()
        );
    }

    @Test
    void adminAlwaysReceivesEveryPermission() {
        Set<PermissionCode> permissions = permissionService.permissionsForRole(Role.ADMIN);

        assertThat(permissions).containsExactlyInAnyOrder(PermissionCode.values());
        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void customerReceivesNoManagementPermission() {
        assertThat(permissionService.permissionsForRole(Role.CUSTOMER)).isEmpty();
        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void cannotEditAdminOrCustomerPermissions() {
        assertThatThrownBy(() -> permissionService.replaceRolePermissions(
                Role.ADMIN,
                Set.of(PermissionCode.ORDER_VIEW)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> permissionService.replaceRolePermissions(
                Role.CUSTOMER,
                Set.of(PermissionCode.ORDER_VIEW)
        )).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void replacesAllPermissionsForConfigurableRole() {
        when(rolePermissionRepository.findPermissionCodesByRole(Role.MANAGER))
                .thenReturn(Set.of(PermissionCode.ORDER_VIEW));

        Set<PermissionCode> result = permissionService.replaceRolePermissions(
                Role.MANAGER,
                Set.of(PermissionCode.ORDER_VIEW, PermissionCode.ORDER_UPDATE)
        );

        assertThat(result).containsExactlyInAnyOrder(
                PermissionCode.ORDER_VIEW,
                PermissionCode.ORDER_UPDATE
        );
        verify(rolePermissionRepository).deleteAllByRole(Role.MANAGER);
        verify(rolePermissionRepository).flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RolePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(RolePermission::getPermissionCode)
                .containsExactlyInAnyOrder(PermissionCode.ORDER_VIEW, PermissionCode.ORDER_UPDATE);
    }
}

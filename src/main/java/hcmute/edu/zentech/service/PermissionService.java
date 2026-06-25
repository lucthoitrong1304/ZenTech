package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.response.CurrentPermissionsResponse;
import hcmute.edu.zentech.dto.response.PermissionItemResponse;
import hcmute.edu.zentech.dto.response.PermissionMatrixResponse;
import hcmute.edu.zentech.dto.response.PermissionModuleResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.RolePermissionRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {
    public static final List<Role> CONFIGURABLE_ROLES = List.of(Role.OWNER, Role.MANAGER, Role.EMPLOYEE);

    private final RolePermissionRepository rolePermissionRepository;
    private final AccountUserRepository accountUserRepository;
    private final AdminActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PermissionMatrixResponse getMatrix() {
        Map<Role, Set<PermissionCode>> matrix = new LinkedHashMap<>();
        matrix.put(Role.ADMIN, allPermissions());
        for (Role role : CONFIGURABLE_ROLES) {
            matrix.put(role, rolePermissionRepository.findPermissionCodesByRole(role));
        }

        Map<String, List<PermissionCode>> grouped = Arrays.stream(PermissionCode.values())
                .collect(Collectors.groupingBy(PermissionCode::getModule, LinkedHashMap::new, Collectors.toList()));
        List<PermissionModuleResponse> modules = grouped.values().stream()
                .map(items -> PermissionModuleResponse.builder()
                        .module(items.getFirst().getModule())
                        .moduleName(items.getFirst().getModuleName())
                        .permissions(items.stream()
                                .map(permission -> PermissionItemResponse.builder()
                                        .code(permission)
                                        .action(permission.getAction())
                                        .description(permission.getDescription())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return PermissionMatrixResponse.builder()
                .configurableRoles(CONFIGURABLE_ROLES)
                .modules(modules)
                .rolePermissions(matrix)
                .build();
    }

    @Transactional
    public Set<PermissionCode> replaceRolePermissions(Role role, Set<PermissionCode> permissions) {
        validateConfigurableRole(role);
        Set<PermissionCode> requested = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(PermissionCode.class)
                : EnumSet.copyOf(permissions);
        Set<PermissionCode> before = rolePermissionRepository.findPermissionCodesByRole(role);

        rolePermissionRepository.deleteAllByRole(role);
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAll(requested.stream()
                .map(permission -> RolePermission.builder().role(role).permissionCode(permission).build())
                .toList());
        logPermissionChange(role, before, requested);
        return requested;
    }

    @Transactional(readOnly = true)
    public CurrentPermissionsResponse getCurrentPermissions() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        AccountUser account = accountUserRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản hiện tại"));
        return CurrentPermissionsResponse.builder()
                .role(account.getRole())
                .permissions(permissionsForRole(account.getRole()))
                .build();
    }

    @Transactional(readOnly = true)
    public Set<PermissionCode> permissionsForRole(Role role) {
        if (role == Role.ADMIN) {
            return allPermissions();
        }
        if (role == null || role == Role.CUSTOMER) {
            return EnumSet.noneOf(PermissionCode.class);
        }
        return rolePermissionRepository.findPermissionCodesByRole(role);
    }

    private Set<PermissionCode> allPermissions() {
        return EnumSet.allOf(PermissionCode.class);
    }

    private void validateConfigurableRole(Role role) {
        if (!CONFIGURABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Chỉ có thể cấu hình quyền cho OWNER, MANAGER và EMPLOYEE");
        }
    }

    private void logPermissionChange(Role role, Set<PermissionCode> before, Set<PermissionCode> after) {
        try {
            String metadata = objectMapper.writeValueAsString(Map.of(
                    "before", sorted(before), "after", sorted(after),
                    "added", difference(after, before), "removed", difference(before, after)
            ));
            activityLogService.log(
                    SecurityContextUtils.getCurrentUserId(), ActivityArea.ADMIN, "PERMISSION",
                    ActivityAction.CHANGE_PERMISSION, ActivitySeverity.SECURITY,
                    "ROLE", role.name(), role.name(),
                    "Cập nhật ma trận phân quyền cho vai trò " + role.name(), metadata
            );
        } catch (JsonProcessingException ignored) {
            activityLogService.log(ActivityAction.CHANGE_PERMISSION, "ROLE", role.name(),
                    "Cập nhật ma trận phân quyền cho vai trò " + role.name());
        }
    }

    private List<String> sorted(Set<PermissionCode> permissions) {
        return permissions.stream().map(Enum::name).sorted().toList();
    }

    private List<String> difference(Set<PermissionCode> left, Set<PermissionCode> right) {
        return left.stream().filter(permission -> !right.contains(permission)).map(Enum::name).sorted().toList();
    }
}

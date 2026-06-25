package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findAllByRole(Role role);

    long countByRole(Role role);

    void deleteAllByRole(Role role);

    default Set<PermissionCode> findPermissionCodesByRole(Role role) {
        return findAllByRole(role).stream()
                .map(RolePermission::getPermissionCode)
                .collect(java.util.stream.Collectors.toSet());
    }
}

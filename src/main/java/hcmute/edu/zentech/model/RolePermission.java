package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_role_permission",
                columnNames = {"role", "permission_code"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_code", nullable = false, length = 60)
    private PermissionCode permissionCode;
}

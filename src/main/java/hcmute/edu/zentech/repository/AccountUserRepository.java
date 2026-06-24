package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AccountUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.projection.AccountSummaryProjection;

@Repository
public interface AccountUserRepository extends JpaRepository<AccountUser, UUID> {
    Optional<AccountUser> findByEmail(String email);
    Optional<AccountUser> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AccountUser> findByRoleInAndIsActiveTrue(List<Role> roles);

    @Query("SELECT a.id as id, a.email as email, a.role as role, a.isActive as isActive, a.createdAt as createdAt, " +
            "CASE WHEN a.role = hcmute.edu.zentech.model.Role.CUSTOMER THEN COALESCE(c.fullName, a.email) " +
            "ELSE COALESCE(e.fullName, a.email) END as displayName, " +
            "CASE WHEN a.role = hcmute.edu.zentech.model.Role.CUSTOMER THEN c.imageUrl " +
            "ELSE e.imageUrl END as imageUrl " +
            "FROM AccountUser a " +
            "LEFT JOIN Employee e ON e.userInfo = a " +
            "LEFT JOIN Customer c ON c.userInfo = a " +
            "WHERE (:keyword IS NULL " +
            "OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:role IS NULL OR a.role = :role) AND " +
            "(:active IS NULL OR a.isActive = :active)")
    Page<AccountSummaryProjection> findAccountsByFilter(
            @Param("keyword") String keyword,
            @Param("role") Role role,
            @Param("active") Boolean active,
            Pageable pageable
    );
}

package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByUserInfo_Id(UUID accountId);

    @EntityGraph(attributePaths = {"userInfo"})
    @Query(
            value = """
                    SELECT e
                    FROM Employee e
                    JOIN e.userInfo u
                    WHERE (:keyword IS NULL
                        OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:active IS NULL OR u.isActive = :active)
                      AND (:role IS NULL OR u.role = :role)
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM Employee e
                    JOIN e.userInfo u
                    WHERE (:keyword IS NULL
                        OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:active IS NULL OR u.isActive = :active)
                      AND (:role IS NULL OR u.role = :role)
                    """
    )
    Page<Employee> searchEmployees(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            @Param("role") Role role,
            Pageable pageable
    );
}

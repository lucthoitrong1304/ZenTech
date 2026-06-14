package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    @Query("SELECT a FROM ActivityLog a " +
           "LEFT JOIN a.user u " +
           "LEFT JOIN Employee e ON e.userInfo = u " +
           "LEFT JOIN Customer c ON c.userInfo = u " +
           "WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CAST(a.action AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CAST(a.area AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(CAST(a.severity AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.module) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.summary) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.targetType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.targetId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.targetLabel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ActivityLog> searchLogs(@Param("search") String search, Pageable pageable);
}

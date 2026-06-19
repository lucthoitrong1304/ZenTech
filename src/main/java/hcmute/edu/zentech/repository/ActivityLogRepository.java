package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    @Query("SELECT a FROM ActivityLog a " +
           "LEFT JOIN a.user u " +
           "LEFT JOIN Employee e ON e.userInfo = u " +
           "LEFT JOIN Customer c ON c.userInfo = u " +
           "WHERE " +
           "(:area IS NULL OR a.area = :area) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:module IS NULL OR :module = '' OR a.module = :module) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) AND " +
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
    Page<ActivityLog> searchLogs(
            @Param("search") String search,
            @Param("area") hcmute.edu.zentech.model.ActivityArea area,
            @Param("severity") hcmute.edu.zentech.model.ActivitySeverity severity,
            @Param("module") String module,
            @Param("action") hcmute.edu.zentech.model.ActivityAction action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("SELECT a FROM ActivityLog a " +
           "LEFT JOIN a.user u " +
           "WHERE " +
           "(:userId IS NULL OR u.id = :userId) AND " +
           "(:email IS NULL OR :email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:module IS NULL OR :module = '' OR a.module = :module) AND " +
           "(:action IS NULL OR a.action = :action)")
    Page<ActivityLog> searchTimeline(
            @Param("userId") UUID userId,
            @Param("email") String email,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("severity") hcmute.edu.zentech.model.ActivitySeverity severity,
            @Param("module") String module,
            @Param("action") hcmute.edu.zentech.model.ActivityAction action,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.module FROM ActivityLog a WHERE a.module IS NOT NULL AND a.module <> '' ORDER BY a.module")
    java.util.List<String> findDistinctModules();

    @Query("SELECT DISTINCT a.action FROM ActivityLog a ORDER BY a.action")
    java.util.List<hcmute.edu.zentech.model.ActivityAction> findDistinctActions();
}


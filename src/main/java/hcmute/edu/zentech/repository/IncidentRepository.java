package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.model.IncidentSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    
    Optional<Incident> findByCode(String code);

    Optional<Incident> findByTraceId(String traceId);

    Optional<Incident> findFirstByApiPathAndHttpMethodAndErrorMessageAndStatusInOrderByCreatedAtDesc(
            String apiPath, String httpMethod, String errorMessage, List<IncidentStatus> statuses
    );

    List<Incident> findByApiPathAndHttpMethodAndStatusInOrderByCreatedAtDesc(
            String apiPath, String httpMethod, List<IncidentStatus> statuses
    );

    @Query("SELECT i FROM Incident i " +
           "LEFT JOIN i.user u " +
           "WHERE " +
           "(:status IS NULL OR i.status = :status) AND " +
           "(:severity IS NULL OR i.severity = :severity) AND " +
           "(:assignee IS NULL OR :assignee = '' OR " +
           "  (:assignee = 'UNASSIGNED' AND i.assignee IS NULL) OR " +
           "  (i.assignee = :assignee)) AND " +
           "(:startDate IS NULL OR i.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR i.createdAt <= :endDate) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "  LOWER(i.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.errorMessage) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.apiPath) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.serviceName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.assignee) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Incident> searchIncidents(
            @Param("status") IncidentStatus status,
            @Param("severity") IncidentSeverity severity,
            @Param("assignee") String assignee,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(i) FROM Incident i")
    long countAllIncidents();
}

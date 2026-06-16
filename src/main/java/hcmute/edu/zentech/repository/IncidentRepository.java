package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    
    Optional<Incident> findByCode(String code);

    Optional<Incident> findByTraceId(String traceId);

    @Query("SELECT COUNT(i) FROM Incident i")
    long countAllIncidents();
}

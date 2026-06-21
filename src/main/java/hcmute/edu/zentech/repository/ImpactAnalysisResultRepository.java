package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ImpactAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImpactAnalysisResultRepository extends JpaRepository<ImpactAnalysisResult, UUID> {
    Optional<ImpactAnalysisResult> findByIncidentId(UUID incidentId);

    @Query("SELECT r FROM ImpactAnalysisResult r " +
           "JOIN r.incident i " +
           "WHERE (:startDate IS NULL OR i.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR i.createdAt <= :endDate)")
    List<ImpactAnalysisResult> findByIncidentDateRange(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}

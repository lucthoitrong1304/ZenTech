package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {
    Optional<AiAnalysis> findByIncidentId(UUID incidentId);
}

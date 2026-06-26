package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AiProductVectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiProductVectorStatusRepository extends JpaRepository<AiProductVectorStatus, UUID> {
    Optional<AiProductVectorStatus> findByVariantId(UUID variantId);

    List<AiProductVectorStatus> findByVariantIdIn(Collection<UUID> variantIds);
}

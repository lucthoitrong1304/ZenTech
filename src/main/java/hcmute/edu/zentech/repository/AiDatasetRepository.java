package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AiDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiDatasetRepository extends JpaRepository<AiDataset, UUID> {
    @Query("""
            select distinct d
            from AiDataset d
            left join fetch d.documents
            order by d.updatedAt desc
            """)
    List<AiDataset> findAllWithDocuments();

    @Query("""
            select distinct d
            from AiDataset d
            left join fetch d.documents
            where d.id = :datasetId
            """)
    Optional<AiDataset> findDetailById(@Param("datasetId") UUID datasetId);
}

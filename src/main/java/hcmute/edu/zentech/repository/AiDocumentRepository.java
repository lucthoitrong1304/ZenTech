package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiDocumentRepository extends JpaRepository<AiDocument, UUID> {
}

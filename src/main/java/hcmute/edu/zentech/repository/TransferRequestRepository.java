package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.TransferRequest;
import hcmute.edu.zentech.model.TransferRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {
    Optional<TransferRequest> findFirstByConversation_IdAndStatusOrderByCreatedAtDesc(
            UUID conversationId,
            TransferRequestStatus status
    );

    @Modifying
    @Query("DELETE FROM TransferRequest tr WHERE tr.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}

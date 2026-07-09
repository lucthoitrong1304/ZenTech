package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {
    @EntityGraph(attributePaths = {"conversation"})
    Optional<ConversationParticipant> findByConversation_IdAndReferenceId(UUID conversationId, UUID referenceId);

    Optional<ConversationParticipant> findByConversation_IdAndUserType(UUID conversationId, ParticipantType userType);

    List<ConversationParticipant> findByConversation_Id(UUID conversationId);

    List<ConversationParticipant> findByConversation_IdAndStatus(UUID conversationId, ParticipantStatus status);

    @Modifying
    @Query("DELETE FROM ConversationParticipant p WHERE p.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}

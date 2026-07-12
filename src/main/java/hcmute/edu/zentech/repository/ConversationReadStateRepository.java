package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ConversationReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationReadStateRepository extends JpaRepository<ConversationReadState, UUID> {
    Optional<ConversationReadState> findByConversation_IdAndAccount_Id(UUID conversationId, UUID accountId);

    List<ConversationReadState> findByConversation_Id(UUID conversationId);

    @Modifying
    @Query("DELETE FROM ConversationReadState r WHERE r.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}

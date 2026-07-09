package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ChatMessageRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChatMessageRecommendationRepository extends JpaRepository<ChatMessageRecommendation, UUID> {
    @Modifying
    @Query("DELETE FROM ChatMessageRecommendation r WHERE r.message.id IN (SELECT m.id FROM ChatMessage m WHERE m.conversation.id = :conversationId)")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}

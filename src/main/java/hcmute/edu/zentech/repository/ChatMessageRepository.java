package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    @EntityGraph(attributePaths = {"conversation", "participant"})
    Page<ChatMessage> findByConversation_IdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    @EntityGraph(attributePaths = {"conversation", "participant"})
    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId AND LOWER(CAST(m.content AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY m.createdAt DESC")
    Page<ChatMessage> searchMessages(@Param("conversationId") UUID conversationId, @Param("keyword") String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"conversation", "participant"})
    List<ChatMessage> findByConversation_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(UUID conversationId, Instant createdAt);

    @EntityGraph(attributePaths = {"conversation", "participant"})
    List<ChatMessage> findTop12ByConversation_IdOrderByCreatedAtDesc(UUID conversationId);
}

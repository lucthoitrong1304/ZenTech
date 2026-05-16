package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    @EntityGraph(attributePaths = {"conversation", "participant"})
    Page<ChatMessage> findByConversation_IdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);
}

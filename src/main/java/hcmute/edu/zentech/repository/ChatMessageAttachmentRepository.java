package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, UUID> {
    List<ChatMessageAttachment> findByMessage_IdOrderBySortOrderAsc(UUID messageId);

    @Modifying
    @Query("DELETE FROM ChatMessageAttachment a WHERE a.message.id IN (SELECT m.id FROM ChatMessage m WHERE m.conversation.id = :conversationId)")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}

package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ChatMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, UUID> {
    List<ChatMessageAttachment> findByMessage_IdOrderBySortOrderAsc(UUID messageId);
}

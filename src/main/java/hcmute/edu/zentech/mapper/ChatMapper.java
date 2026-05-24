package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.ChatAttachmentResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.ParticipantResponse;
import hcmute.edu.zentech.dto.response.TransferRequestResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatMessageAttachment;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.TransferRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatMapper {
    public ConversationResponse toConversationResponse(
            Conversation conversation,
            List<ConversationParticipant> participants
    ) {
        Customer customer = conversation.getCustomer();
        AccountUser account = customer != null ? customer.getUserInfo() : null;

        return ConversationResponse.builder()
                .id(conversation.getId())
                .customerId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getFullName() : null)
                .customerEmail(account != null ? account.getEmail() : null)
                .status(conversation.getStatus())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .closedAt(conversation.getClosedAt())
                .participants(participants == null ? List.of() : participants.stream()
                        .map(this::toParticipantResponse)
                        .toList())
                .build();
    }

    public ParticipantResponse toParticipantResponse(ConversationParticipant participant) {
        return ParticipantResponse.builder()
                .id(participant.getId())
                .userType(participant.getUserType())
                .referenceId(participant.getReferenceId())
                .status(participant.getStatus())
                .joinedAt(participant.getJoinedAt())
                .leftAt(participant.getLeftAt())
                .build();
    }

    public ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        ConversationParticipant participant = message.getParticipant();

        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .participantId(participant != null ? participant.getId() : null)
                .senderType(participant != null ? participant.getUserType() : null)
                .senderReferenceId(participant != null ? participant.getReferenceId() : null)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .attachments(message.getAttachments() == null ? List.of() : message.getAttachments().stream()
                        .map(this::toChatAttachmentResponse)
                        .toList())
                .createdAt(message.getCreatedAt())
                .deletedAt(message.getDeletedAt())
                .build();
    }

    public ChatAttachmentResponse toChatAttachmentResponse(ChatMessageAttachment attachment) {
        return ChatAttachmentResponse.builder()
                .id(attachment.getId())
                .fileKey(attachment.getFileKey())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .attachmentType(attachment.getAttachmentType())
                .sortOrder(attachment.getSortOrder())
                .build();
    }

    public TransferRequestResponse toTransferRequestResponse(TransferRequest request) {
        return TransferRequestResponse.builder()
                .id(request.getId())
                .conversationId(request.getConversation().getId())
                .requestedBy(request.getRequestedBy())
                .requestedTo(request.getRequestedTo())
                .reason(request.getReason())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .resolvedAt(request.getResolvedAt())
                .build();
    }
}

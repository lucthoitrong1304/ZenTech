package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ChatAttachmentRequest;
import hcmute.edu.zentech.dto.request.ChatMessageRequest;
import hcmute.edu.zentech.dto.response.ChatAttachmentResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.ChatAttachmentType;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatMessageAttachment;
import hcmute.edu.zentech.model.ChatMessageType;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ConversationStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.repository.ChatMessageAttachmentRepository;
import hcmute.edu.zentech.repository.ChatMessageRepository;
import hcmute.edu.zentech.repository.ConversationParticipantRepository;
import hcmute.edu.zentech.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatParticipantService chatParticipantService;
    private final ChatBotService chatBotService;
    private final ChatMapper chatMapper;
    private final R2StorageService r2StorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMessagesForCurrentUser(UUID conversationId, int page, int size) {
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.findCurrentCustomer()
                .ifPresentOrElse(
                        customer -> chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId()),
                        chatParticipantService::getCurrentStaffIdentity
                );
        return getMessages(conversationId, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> searchMessagesForCurrentUser(UUID conversationId, String keyword, int page, int size) {
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.findCurrentCustomer()
                .ifPresentOrElse(
                        customer -> chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId()),
                        chatParticipantService::getCurrentStaffIdentity
                );
                
        Page<ChatMessage> messagePage = chatMessageRepository.searchMessages(
                conversationId,
                keyword,
                PageRequest.of(
                        Math.max(page, DEFAULT_PAGE),
                        normalizeSize(size)
                )
        );
        return PageResponse.from(messagePage, messagePage.getContent().stream()
                .map(this::toChatMessageResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessageContextForCurrentUser(UUID conversationId, UUID messageId) {
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.findCurrentCustomer()
                .ifPresentOrElse(
                        customer -> chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId()),
                        chatParticipantService::getCurrentStaffIdentity
                );
                
        ChatMessage targetMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));
                
        if (!targetMessage.getConversation().getId().equals(conversationId)) {
            throw new IllegalArgumentException("Message does not belong to the specified conversation");
        }
        
        List<ChatMessage> contextMessages = chatMessageRepository.findByConversation_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                conversationId, targetMessage.getCreatedAt());
                
        return contextMessages.stream()
                .map(this::toChatMessageResponse)
                .toList();
    }

    @Transactional
    public ChatMessageResponse sendMessage(UUID conversationId, ChatMessageRequest request, UUID accountId) {
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);
        ConversationParticipant participant = chatParticipantService.getActiveParticipant(conversationId, accountId);
        validateMessage(request, accountId);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .participant(participant)
                .messageType(request.getMessageType())
                .content(normalizeContent(request.getContent()))
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        savedMessage.setAttachments(saveAttachments(savedMessage, request.getAttachments()));
        
        if (request.getMessageType() == ChatMessageType.TEXT) {
            conversation.setTitle(savedMessage.getContent());
        } else {
            conversation.setTitle("Đã gửi đính kèm");
        }
        
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        ChatMessageResponse response = toChatMessageResponse(savedMessage);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        
        List<ConversationParticipant> participants = participantRepository.findByConversation_Id(conversationId);
        ConversationResponse convResponse = chatMapper.toConversationResponse(conversation, participants);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", convResponse);

        if (conversation.getStatus() == ConversationStatus.BOT_CONSULTING
                && participant.getUserType() == ParticipantType.CUSTOMER) {
            chatBotService.handleCustomerMessage(conversationId, response)
                    .ifPresent(botResponse -> messagingTemplate.convertAndSend(
                            "/topic/conversations." + conversationId,
                            botResponse
                    ));
        }

        // Send Notification to other participants
        sendNotificationsToOtherParticipants(conversationId, participant.getId(), savedMessage);

        return response;
    }

    private void sendNotificationsToOtherParticipants(UUID conversationId, UUID senderParticipantId, ChatMessage message) {
        List<ConversationParticipant> otherParticipants = participantRepository.findByConversation_Id(conversationId).stream()
                .filter(p -> !p.getId().equals(senderParticipantId))
                .toList();

        for (ConversationParticipant p : otherParticipants) {
            chatParticipantService.resolveAccountId(p.getUserType(), p.getReferenceId())
                    .ifPresent(accountId -> {
                        String title = "Tin nhắn mới từ " + (message.getParticipant().getUserType() == ParticipantType.CUSTOMER ? "Khách hàng" : "Nhân viên");
                        String content = message.getMessageType() == hcmute.edu.zentech.model.ChatMessageType.TEXT ? 
                                         message.getContent() : "Đã gửi một tệp đính kèm";
                        notificationService.createNotification(
                                accountId,
                                title,
                                content,
                                hcmute.edu.zentech.model.NotificationType.CHAT_MESSAGE,
                                conversationId
                        );
                    });
        }
    }

    private PageResponse<ChatMessageResponse> getMessages(UUID conversationId, int page, int size) {
        Page<ChatMessage> messagePage = chatMessageRepository.findByConversation_IdOrderByCreatedAtAsc(
                conversationId,
                PageRequest.of(
                        Math.max(page, DEFAULT_PAGE),
                        normalizeSize(size),
                        Sort.by(Sort.Direction.ASC, "createdAt", "id")
                )
        );
        return PageResponse.from(messagePage, messagePage.getContent().stream()
                .map(this::toChatMessageResponse)
                .toList());
    }

    private Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
    }

    private void validateMessage(ChatMessageRequest request, UUID accountId) {
        if (request.getMessageType() == ChatMessageType.SYSTEM) {
            throw new AccessDeniedException("System messages cannot be sent by users");
        }

        List<ChatAttachmentRequest> attachments = getAttachments(request);
        boolean hasContent = normalizeContent(request.getContent()) != null;
        boolean hasAttachments = !attachments.isEmpty();

        if (!hasContent && !hasAttachments) {
            throw new IllegalArgumentException("Message content or attachments are required");
        }

        if (request.getMessageType() == ChatMessageType.TEXT && hasAttachments) {
            throw new IllegalArgumentException("Text messages cannot include attachments");
        }

        if (request.getMessageType() != ChatMessageType.TEXT && request.getMessageType() != ChatMessageType.CALL && !hasAttachments) {
            throw new IllegalArgumentException("Media messages require at least one attachment");
        }

        if (attachments.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new IllegalArgumentException("A message can include up to 10 attachments");
        }

        for (ChatAttachmentRequest attachment : attachments) {
            validateAttachmentMatchesMessageType(request.getMessageType(), attachment.getAttachmentType());
            r2StorageService.validateUploadedChatAttachment(
                    attachment.getFileKey(),
                    accountId,
                    attachment.getContentType(),
                    attachment.getFileSize(),
                    attachment.getAttachmentType()
            );
        }
    }

    private List<ChatMessageAttachment> saveAttachments(
            ChatMessage message,
            List<ChatAttachmentRequest> attachmentRequests
    ) {
        List<ChatAttachmentRequest> requests = attachmentRequests == null ? List.of() : attachmentRequests;
        if (requests.isEmpty()) {
            return List.of();
        }

        List<ChatMessageAttachment> attachments = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            ChatAttachmentRequest request = requests.get(i);
            attachments.add(ChatMessageAttachment.builder()
                    .message(message)
                    .fileKey(request.getFileKey())
                    .fileName(normalizeRequiredText(request.getFileName()))
                    .contentType(request.getContentType())
                    .fileSize(request.getFileSize())
                    .attachmentType(request.getAttachmentType())
                    .sortOrder(i)
                    .build());
        }

        return attachmentRepository.saveAll(attachments);
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        ChatMessageResponse response = chatMapper.toChatMessageResponse(message);
        if (response.getAttachments() != null) {
            response.getAttachments().forEach(this::attachMediaUrl);
        }
        return response;
    }

    private void attachMediaUrl(ChatAttachmentResponse attachment) {
        attachment.setMediaUrl(r2StorageService.getPresignedGetUrl(attachment.getFileKey()));
    }

    private void validateAttachmentMatchesMessageType(
            ChatMessageType messageType,
            ChatAttachmentType attachmentType
    ) {
        if (messageType == ChatMessageType.MEDIA) {
            return;
        }

        if (messageType == ChatMessageType.IMAGE && attachmentType == ChatAttachmentType.IMAGE) {
            return;
        }

        if (messageType == ChatMessageType.VIDEO && attachmentType == ChatAttachmentType.VIDEO) {
            return;
        }

        if (messageType == ChatMessageType.FILE && attachmentType == ChatAttachmentType.FILE) {
            return;
        }

        throw new IllegalArgumentException("Attachment type does not match message type");
    }

    private void ensureNotClosed(Conversation conversation) {
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new AccessDeniedException("Conversation is closed");
        }
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private List<ChatAttachmentRequest> getAttachments(ChatMessageRequest request) {
        return request.getAttachments() == null ? List.of() : request.getAttachments();
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }

        String trimmedContent = content.trim();
        return trimmedContent.isEmpty() ? null : trimmedContent;
    }

    private String normalizeRequiredText(String text) {
        return text == null ? "" : text.trim();
    }
}

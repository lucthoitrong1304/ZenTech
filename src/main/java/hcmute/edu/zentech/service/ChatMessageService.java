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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final ConversationReadStateService conversationReadStateService;
    private final TransactionTemplate transactionTemplate;

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
    public PageResponse<ChatMessageResponse> getMessagesForManagement(UUID conversationId, int page, int size) {
        getConversation(conversationId);
        chatParticipantService.getCurrentStaffIdentity();
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

    public ChatMessageResponse sendMessage(UUID conversationId, ChatMessageRequest request, UUID accountId) {
        PersistedMessage persistedMessage = transactionTemplate.execute(status -> persistMessage(
                conversationId,
                request,
                accountId
        ));
        if (persistedMessage == null) {
            throw new IllegalStateException("Message could not be saved");
        }

        broadcastMessage(conversationId, persistedMessage.message());
        messagingTemplate.convertAndSend("/topic/management.chat.queue", persistedMessage.conversation());
        messagingTemplate.convertAndSend(
                "/topic/customer.chat." + persistedMessage.customerAccountId(),
                persistedMessage.conversation());

        if (persistedMessage.shouldTriggerBot()) {
            try {
                chatBotService.handleCustomerMessage(
                        conversationId,
                        persistedMessage.message(),
                        persistedMessage.pageContext(),
                        accountId,
                        persistedMessage.traceId()
                );
            } catch (RuntimeException ex) {
                log.warn("Failed to trigger bot response for conversation {}", conversationId, ex);
            }
        }

        sendNotificationsToOtherParticipants(
                conversationId,
                persistedMessage.senderParticipantId(),
                persistedMessage.message()
        );

        return persistedMessage.message();
    }

    private PersistedMessage persistMessage(UUID conversationId, ChatMessageRequest request, UUID accountId) {
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);
        ensureNotArchived(conversation);
        ConversationParticipant participant = chatParticipantService.getActiveParticipant(conversationId, accountId);
        validateMessage(request, accountId);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .participant(participant)
                .messageType(request.getMessageType())
                .content(normalizeContent(request.getContent()))
                .build();

        ChatMessage savedMessage = chatMessageRepository.saveAndFlush(message);
        savedMessage.setAttachments(saveAttachments(savedMessage, request.getAttachments()));
        
        if (request.getMessageType() == ChatMessageType.TEXT) {
            conversation.setTitle(savedMessage.getContent());
        } else {
            conversation.setTitle("Đã gửi đính kèm");
        }
        
        conversation.setUpdatedAt(Instant.now());
        Conversation savedConversation = conversationRepository.saveAndFlush(conversation);
        conversationReadStateService.incrementRecipients(savedConversation, accountId);

        ChatMessageResponse response = toChatMessageResponse(savedMessage);
        response.setTraceId(request.getTraceId());

        List<ConversationParticipant> participants = participantRepository.findByConversation_Id(conversationId);
        ConversationResponse convResponse = chatMapper.toConversationResponse(savedConversation, participants);
        boolean shouldTriggerBot = savedConversation.getStatus() == ConversationStatus.BOT_CONSULTING
                && participant.getUserType() == ParticipantType.CUSTOMER;

        return new PersistedMessage(
                response,
                convResponse,
                participant.getId(),
                savedConversation.getCustomer().getUserInfo().getId(),
                shouldTriggerBot,
                request.getPageContext(),
                request.getTraceId());
    }

    private void broadcastMessage(UUID conversationId, ChatMessageResponse response) {
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
    }

    private void sendNotificationsToOtherParticipants(
            UUID conversationId,
            UUID senderParticipantId,
            ChatMessageResponse message
    ) {
        List<ConversationParticipant> otherParticipants = participantRepository.findByConversation_Id(conversationId).stream()
                .filter(p -> !p.getId().equals(senderParticipantId))
                .toList();

        for (ConversationParticipant p : otherParticipants) {
            chatParticipantService.resolveAccountId(p.getUserType(), p.getReferenceId())
                    .ifPresent(accountId -> {
                        try {
                            String title = "Tin nhắn mới từ " + (message.getSenderType() == ParticipantType.CUSTOMER ? "Khách hàng" : "Nhân viên");
                            String content = message.getMessageType() == ChatMessageType.TEXT
                                    ? message.getContent()
                                    : "Đã gửi một tệp đính kèm";
                            notificationService.createNotification(
                                    accountId,
                                    title,
                                    content,
                                    hcmute.edu.zentech.model.NotificationType.CHAT_MESSAGE,
                                    conversationId
                            );
                        } catch (RuntimeException ex) {
                            log.warn("Failed to create chat notification for account {} in conversation {}", accountId, conversationId, ex);
                        }
                    });
        }
    }

    private record PersistedMessage(
            ChatMessageResponse message,
            ConversationResponse conversation,
            UUID senderParticipantId,
            UUID customerAccountId,
            boolean shouldTriggerBot,
            Map<String, Object> pageContext,
            String traceId
    ) {
    }

    private PageResponse<ChatMessageResponse> getMessages(UUID conversationId, int page, int size) {
        Page<ChatMessage> messagePage = chatMessageRepository.findByConversation_Id(
                conversationId,
                PageRequest.of(
                        Math.max(page, DEFAULT_PAGE),
                        normalizeSize(size),
                        Sort.by(Sort.Direction.DESC, "createdAt", "id")
                )
        );
        return PageResponse.from(messagePage, messagePage.getContent().stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId))
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

        if (request.getMessageType() != ChatMessageType.TEXT && !hasAttachments) {
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

    private void ensureNotArchived(Conversation conversation) {
        if (conversation.isArchived()) {
            throw new AccessDeniedException("Conversation is archived");
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

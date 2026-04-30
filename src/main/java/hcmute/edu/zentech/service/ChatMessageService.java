package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ChatMessageRequest;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatMessageType;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ConversationStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.repository.ChatMessageRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatParticipantService chatParticipantService;
    private final ChatBotService chatBotService;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;

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

    // Logic gửi 1 tin nhắn
    @Transactional
    public ChatMessageResponse sendMessage(UUID conversationId, ChatMessageRequest request, UUID accountId) {
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);
        // Lấy cuộc hội thoại.
        ConversationParticipant participant = chatParticipantService.getActiveParticipant(conversationId, accountId);
        validateMessage(request);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .participant(participant)
                .messageType(request.getMessageType())
                .content(request.getContent().trim())
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        ChatMessageResponse response = chatMapper.toChatMessageResponse(savedMessage);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);

        // Nếu đây đang ở chế độ AI tư vấn => AI sẽ xử lý sau đó phản hồi về cho người dùng.
        if (conversation.getStatus() == ConversationStatus.BOT_CONSULTING
                && participant.getUserType() == ParticipantType.CUSTOMER) {
            chatBotService.handleCustomerMessage(conversationId, response)
                    .ifPresent(botResponse -> messagingTemplate.convertAndSend(
                            "/topic/conversations/" + conversationId,
                            botResponse
                    ));
        }

        return response;
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
                .map(chatMapper::toChatMessageResponse)
                .toList());
    }

    private Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
    }

    // Xác thực message
    private void validateMessage(ChatMessageRequest request) {
        if (request.getMessageType() == ChatMessageType.SYSTEM) {
            throw new AccessDeniedException("System messages cannot be sent by users");
        }
    }

    // Đảm bảo cuộc hội thoại hiện tại không đóng.
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
}

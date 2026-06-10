package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.AiAgentRuntimeRequest;
import hcmute.edu.zentech.dto.response.ChatAttachmentResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatAttachmentType;
import hcmute.edu.zentech.model.ChatMessageType;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.ChatMessageRepository;
import hcmute.edu.zentech.repository.ConversationParticipantRepository;
import hcmute.edu.zentech.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBotService {
    private static final String FALLBACK_REPLY = "ZenTech AI đang bận. Bạn có thể thử lại hoặc yêu cầu nhân viên hỗ trợ.";
    private static final String UNSUPPORTED_ATTACHMENT_REPLY = "Hiện tại ZenTech AI chỉ phân tích được ảnh, PDF, TXT và MD. Bạn vui lòng mô tả nội dung file hoặc yêu cầu nhân viên hỗ trợ nhé.";
    private static final int MAX_BOT_REPLY_LENGTH = 5000;
    private static final int MAX_ANALYZABLE_ATTACHMENTS = 3;

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMapper chatMapper;
    private final R2StorageService r2StorageService;
    private final AiManagementService aiManagementService;
    private final TransactionTemplate transactionTemplate;

    public Optional<ChatMessageResponse> handleCustomerMessage(UUID conversationId, ChatMessageResponse message) {
        log.info("Starting handleCustomerMessage for conversation: {}, messageId: {}", conversationId, message.getId());
        String customerContent = normalizeContent(message.getContent());
        List<AiAgentRuntimeRequest.Attachment> attachments = buildAnalyzableAttachments(message);
        boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();

        if (!isBotSupportedMessageType(message.getMessageType())) {
            log.warn("Unsupported customer message type {} for conversation: {}", message.getMessageType(), conversationId);
            return Optional.empty();
        }

        Optional<ConversationParticipant> botParticipant = participantRepository
                .findByConversation_IdAndUserType(conversationId, ParticipantType.BOT)
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE);

        if (botParticipant.isEmpty()) {
            log.info("No active bot participant found for conversation: {}", conversationId);
            return Optional.empty();
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) {
            return Optional.empty();
        }

        if (customerContent == null && attachments.isEmpty()) {
            log.warn("Customer message has no readable content for conversation: {}", conversationId);
            if (hasAttachments) {
                return transactionTemplate.execute(status -> saveBotMessage(
                        conversation.getId(),
                        botParticipant.get().getId(),
                        UNSUPPORTED_ATTACHMENT_REPLY
                ));
            }
            return Optional.empty();
        }

        if (hasAttachments && attachments.isEmpty()) {
            return transactionTemplate.execute(status -> saveBotMessage(
                    conversation.getId(),
                    botParticipant.get().getId(),
                    UNSUPPORTED_ATTACHMENT_REPLY
            ));
        }

        String prompt = buildCustomerPrompt(message, customerContent);
        log.info("Requesting AI reply for conversation: {}", conversationId);
        String replyContent = requestAiReply(conversationId, message, prompt, attachments)
                .orElse(FALLBACK_REPLY);
        log.info("Received reply content for conversation: {}", conversationId);

        return transactionTemplate.execute(status -> saveBotMessage(
                conversation.getId(),
                botParticipant.get().getId(),
                replyContent
        ));
    }

    private Optional<ChatMessageResponse> saveBotMessage(
            UUID conversationId,
            UUID botParticipantId,
            String replyContent
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.warn("Conversation {} not found", conversationId);
            return Optional.empty();
        }

        ConversationParticipant botParticipant = participantRepository.findById(botParticipantId)
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
                .orElse(null);
        if (botParticipant == null) {
            return Optional.empty();
        }

        ChatMessage botMessage = ChatMessage.builder()
                .conversation(conversation)
                .participant(botParticipant)
                .messageType(ChatMessageType.TEXT)
                .content(limitContent(replyContent))
                .build();

        ChatMessage savedMessage = chatMessageRepository.saveAndFlush(botMessage);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.saveAndFlush(conversation);

        return Optional.of(chatMapper.toChatMessageResponse(savedMessage));
    }

    private Optional<String> requestAiReply(
            UUID conversationId,
            ChatMessageResponse message,
            String customerContent,
            List<AiAgentRuntimeRequest.Attachment> attachments
    ) {
        try {
            List<AiAgentRuntimeRequest.HistoryMessage> history = loadHistory(conversationId, message.getId());
            log.info("Loaded {} history messages for conversation: {}", history.size(), conversationId);

            Role role = determineRole(message.getSenderType());
            return aiManagementService.generateRuntimeReply(
                    role,
                    customerContent,
                    history,
                    attachments,
                    java.util.Map.of("conversationId", conversationId.toString())
            );
        } catch (Exception ex) {
            log.warn("AI service failed for conversation {}", conversationId, ex);
            return Optional.empty();
        }
    }

    private Role determineRole(ParticipantType participantType) {
        if (participantType == null) {
            return Role.CUSTOMER;
        }
        return switch (participantType) {
            case EMPLOYEE, EXPERT -> Role.EMPLOYEE;
            default -> Role.CUSTOMER;
        };
    }

    private boolean isBotSupportedMessageType(ChatMessageType messageType) {
        return messageType == ChatMessageType.TEXT
                || messageType == ChatMessageType.IMAGE
                || messageType == ChatMessageType.FILE
                || messageType == ChatMessageType.MEDIA;
    }

    private List<AiAgentRuntimeRequest.Attachment> buildAnalyzableAttachments(ChatMessageResponse message) {
        List<ChatAttachmentResponse> source = message.getAttachments() == null ? List.of() : message.getAttachments();
        if (source.isEmpty()) {
            return List.of();
        }

        List<AiAgentRuntimeRequest.Attachment> attachments = new ArrayList<>();
        for (ChatAttachmentResponse attachment : source) {
            if (attachments.size() >= MAX_ANALYZABLE_ATTACHMENTS) {
                break;
            }

            AiAgentRuntimeRequest.Attachment aiAttachment = buildAnalyzableAttachment(attachment);
            if (aiAttachment != null) {
                attachments.add(aiAttachment);
            }
        }
        return attachments;
    }

    private AiAgentRuntimeRequest.Attachment buildAnalyzableAttachment(ChatAttachmentResponse attachment) {
        if (attachment.getAttachmentType() == ChatAttachmentType.IMAGE) {
            String mediaUrl = normalizeContent(attachment.getMediaUrl());
            if (mediaUrl == null) {
                return null;
            }
            return AiAgentRuntimeRequest.Attachment.builder()
                    .fileName(attachment.getFileName())
                    .contentType(attachment.getContentType())
                    .attachmentType(attachment.getAttachmentType())
                    .mediaUrl(mediaUrl)
                    .build();
        }

        if (attachment.getAttachmentType() != ChatAttachmentType.FILE || !isSupportedDocument(attachment)) {
            return null;
        }

        try {
            byte[] raw = r2StorageService.getObjectBytes(attachment.getFileKey());
            return AiAgentRuntimeRequest.Attachment.builder()
                    .fileName(attachment.getFileName())
                    .contentType(attachment.getContentType())
                    .attachmentType(attachment.getAttachmentType())
                    .contentBase64(Base64.getEncoder().encodeToString(raw))
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Could not read chat attachment {} for AI analysis", attachment.getFileKey(), ex);
            return null;
        }
    }

    private boolean isSupportedDocument(ChatAttachmentResponse attachment) {
        String contentType = Optional.ofNullable(attachment.getContentType())
                .orElse("")
                .toLowerCase(Locale.ROOT);
        String fileName = Optional.ofNullable(attachment.getFileName())
                .orElse("")
                .toLowerCase(Locale.ROOT);

        return contentType.equals("application/pdf")
                || contentType.equals("text/plain")
                || contentType.equals("text/markdown")
                || fileName.endsWith(".pdf")
                || fileName.endsWith(".txt")
                || fileName.endsWith(".md");
    }

    private String buildCustomerPrompt(ChatMessageResponse message, String customerContent) {
        String normalized = normalizeContent(customerContent);
        List<ChatAttachmentResponse> attachments = message.getAttachments() == null ? List.of() : message.getAttachments();
        if (attachments.isEmpty()) {
            return normalized;
        }

        String attachmentNames = attachments.stream()
                .map(ChatAttachmentResponse::getFileName)
                .map(this::normalizeContent)
                .filter(name -> name != null)
                .reduce((left, right) -> left + ", " + right)
                .orElse("tep dinh kem");

        if (normalized == null || normalized.equals(attachmentNames)) {
            return "Khách hàng đã gửi tệp đính kèm: " + attachmentNames + ". Hãy phân tích nội dung và hỗ trợ ngắn gọn.";
        }

        return normalized;
    }

    private List<AiAgentRuntimeRequest.HistoryMessage> loadHistory(UUID conversationId, UUID currentMessageId) {
        return chatMessageRepository.findTop12ByConversation_IdOrderByCreatedAtDesc(conversationId).stream()
                .filter(message -> !message.getId().equals(currentMessageId))
                .filter(message -> message.getMessageType() == ChatMessageType.TEXT)
                .filter(message -> normalizeContent(message.getContent()) != null)
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(message -> AiAgentRuntimeRequest.HistoryMessage.builder()
                        .role(toAiRole(message.getParticipant()))
                        .content(normalizeContent(message.getContent()))
                        .build())
                .toList();
    }

    private String toAiRole(ConversationParticipant participant) {
        if (participant == null) {
            return "system";
        }
        if (participant.getUserType() == ParticipantType.BOT) {
            return "assistant";
        }
        if (participant.getUserType() == ParticipantType.CUSTOMER) {
            return "customer";
        }
        return "staff";
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmedContent = content.trim();
        return trimmedContent.isEmpty() ? null : trimmedContent;
    }

    private String limitContent(String content) {
        if (content.length() <= MAX_BOT_REPLY_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_BOT_REPLY_LENGTH);
    }
}

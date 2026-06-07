package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.AiChatRespondRequest;
import hcmute.edu.zentech.dto.response.AiChatRespondResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.ChatMessage;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBotService {
    private static final String FALLBACK_REPLY = "ZenTech AI dang ban. Ban co the thu lai hoac yeu cau nhan vien ho tro.";
    private static final int MAX_BOT_REPLY_LENGTH = 5000;

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;

    private final ConversationParticipantRepository participantRepository;
    private final ChatMapper chatMapper;
    private final ObjectMapper objectMapper;
    private final AiManagementService aiManagementService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${app.ai.timeout-ms:15000}")
    private long aiTimeoutMs;

    public Optional<ChatMessageResponse> handleCustomerMessage(UUID conversationId, ChatMessageResponse message) {
        log.info("Starting handleCustomerMessage for conversation: {}, messageId: {}", conversationId, message.getId());
        String customerContent = normalizeContent(message.getContent());
        if (customerContent == null || message.getMessageType() != ChatMessageType.TEXT) {
            log.warn("Invalid customer content or message type for conversation: {}", conversationId);
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

        log.info("Requesting AI reply for conversation: {}", conversationId);
        String replyContent = requestAiReply(conversationId, message, customerContent)
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
            String customerContent
    ) {
        try {
            List<AiChatRespondRequest.HistoryMessage> history = loadHistory(conversationId, message.getId());
            log.info("Loaded {} history messages for conversation: {}", history.size(), conversationId);
            Optional<String> runtimeReply = aiManagementService.generateRuntimeReply(
                    Role.CUSTOMER,
                    customerContent,
                    history,
                    java.util.Map.of("conversationId", conversationId.toString())
            );
            if (runtimeReply.isPresent()) {
                log.info("Generated runtime reply for conversation: {}", conversationId);
                return runtimeReply;
            }

            log.info("Preparing request to external AI service for conversation: {}", conversationId);
            AiChatRespondRequest request = AiChatRespondRequest.builder()
                    .conversationId(conversationId)
                    .messageId(message.getId())
                    .message(customerContent)
                    .history(history)
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/chat/respond"))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            log.info("Sending POST request to AI service: {}/chat/respond", normalizeBaseUrl(aiBaseUrl));
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("Received response from AI service with status code: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI service returned status {} for conversation {}", response.statusCode(), conversationId);
                return Optional.empty();
            }

            AiChatRespondResponse aiResponse = objectMapper.readValue(response.body(), AiChatRespondResponse.class);
            return Optional.ofNullable(normalizeContent(aiResponse.getContent()));
        } catch (Exception ex) {
            log.warn("AI service failed for conversation {}", conversationId, ex);
            return Optional.empty();
        }
    }

    private List<AiChatRespondRequest.HistoryMessage> loadHistory(UUID conversationId, UUID currentMessageId) {
        return chatMessageRepository.findTop12ByConversation_IdOrderByCreatedAtDesc(conversationId).stream()
                .filter(message -> !message.getId().equals(currentMessageId))
                .filter(message -> message.getMessageType() == ChatMessageType.TEXT)
                .filter(message -> normalizeContent(message.getContent()) != null)
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(message -> AiChatRespondRequest.HistoryMessage.builder()
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

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
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

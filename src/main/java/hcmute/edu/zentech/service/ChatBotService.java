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
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${app.ai.timeout-ms:15000}")
    private long aiTimeoutMs;

    @Transactional
    public Optional<ChatMessageResponse> handleCustomerMessage(UUID conversationId, ChatMessageResponse message) {
        String customerContent = normalizeContent(message.getContent());
        if (customerContent == null || message.getMessageType() != ChatMessageType.TEXT) {
            return Optional.empty();
        }

        Optional<ConversationParticipant> botParticipant = participantRepository
                .findByConversation_IdAndUserType(conversationId, ParticipantType.BOT)
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE);

        if (botParticipant.isEmpty()) {
            return Optional.empty();
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) {
            return Optional.empty();
        }

        String replyContent = requestAiReply(conversationId, message, customerContent)
                .orElse(FALLBACK_REPLY);

        ChatMessage botMessage = ChatMessage.builder()
                .conversation(conversation)
                .participant(botParticipant.get())
                .messageType(ChatMessageType.TEXT)
                .content(limitContent(replyContent))
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(botMessage);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        return Optional.of(chatMapper.toChatMessageResponse(savedMessage));
    }

    private Optional<String> requestAiReply(
            UUID conversationId,
            ChatMessageResponse message,
            String customerContent
    ) {
        try {
            List<AiChatRespondRequest.HistoryMessage> history = loadHistory(conversationId, message.getId());
            Optional<String> runtimeReply = aiManagementService.generateRuntimeReply(
                    Role.CUSTOMER,
                    customerContent,
                    history,
                    java.util.Map.of("conversationId", conversationId.toString())
            );
            if (runtimeReply.isPresent()) {
                return runtimeReply;
            }

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

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

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

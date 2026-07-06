package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.AiAgentRuntimeRequest;
import hcmute.edu.zentech.dto.response.ChatAttachmentResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatMessageRecommendation;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final ChatConversationService chatConversationService;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Async
    public void handleCustomerMessage(
            UUID conversationId,
            ChatMessageResponse message,
            Map<String, Object> pageContext,
            UUID accountId
    ) {
        log.info("Starting async handleCustomerMessage for conversation: {}, messageId: {}", conversationId, message.getId());
        String customerContent = normalizeContent(message.getContent());
        List<AiAgentRuntimeRequest.Attachment> attachments = buildAnalyzableAttachments(message);
        boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();

        if (!isBotSupportedMessageType(message.getMessageType())) {
            log.warn("Unsupported customer message type {} for conversation: {}", message.getMessageType(), conversationId);
            return;
        }

        Optional<ConversationParticipant> botParticipant = participantRepository
                .findByConversation_IdAndUserType(conversationId, ParticipantType.BOT)
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE);

        if (botParticipant.isEmpty()) {
            log.info("No active bot participant found for conversation: {}", conversationId);
            return;
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) {
            return;
        }

        if (customerContent == null && attachments.isEmpty()) {
            log.warn("Customer message has no readable content for conversation: {}", conversationId);
            if (hasAttachments) {
                transactionTemplate.execute(status -> saveAndBroadcastBotMessage(
                        conversation.getId(),
                        botParticipant.get().getId(),
                        UNSUPPORTED_ATTACHMENT_REPLY
                ));
            }
            return;
        }

        if (hasAttachments && attachments.isEmpty()) {
            transactionTemplate.execute(status -> saveAndBroadcastBotMessage(
                    conversation.getId(),
                    botParticipant.get().getId(),
                    UNSUPPORTED_ATTACHMENT_REPLY
            ));
            return;
        }

        String prompt = buildCustomerPrompt(message, customerContent);
        log.info("Requesting AI reply stream for conversation: {}", conversationId);

        List<AiAgentRuntimeRequest.HistoryMessage> history = loadHistory(conversationId, message.getId());
        Role role = determineRole(message.getSenderType());

        Optional<java.net.http.HttpResponse<java.io.InputStream>> streamResponseOpt = aiManagementService.requestAiReplyStream(
                role,
                prompt,
                history,
                attachments,
                buildBusinessContext(conversationId, role, accountId, pageContext)
        );

        if (streamResponseOpt.isEmpty()) {
            log.warn("Failed to get stream response, falling back for conversation: {}", conversationId);
            transactionTemplate.execute(status -> saveAndBroadcastBotMessage(
                    conversation.getId(),
                    botParticipant.get().getId(),
                    FALLBACK_REPLY
            ));
            return;
        }

        java.io.InputStream inputStream = streamResponseOpt.get().body();
        StringBuilder accumulatedContent = new StringBuilder();
        List<RecommendationPayload> recommendations = new ArrayList<>();
        boolean handoffRecommended = false;
        try (inputStream; BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            String event = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    JsonNode data = objectMapper.readTree(line.substring("data:".length()).trim());
                    if ("chunk".equals(event)) {
                        String chunk = data.path("content").asText("");
                        if (!chunk.isEmpty()) {
                            accumulatedContent.append(chunk);
                            broadcastChunk(conversationId, botParticipant.get().getId(), chunk);
                        }
                    } else if ("complete".equals(event)) {
                        handoffRecommended = data.path("handoffRecommended").asBoolean(false);
                        for (JsonNode product : data.path("recommendedProducts")) {
                            RecommendationPayload payload = RecommendationPayload.from(product);
                            if (payload != null) {
                                recommendations.add(payload);
                            }
                        }
                    }
                } else if (!line.isBlank() && event == null) {
                    // Compatibility with older AI deployments that stream plain text.
                    accumulatedContent.append(line);
                    broadcastChunk(conversationId, botParticipant.get().getId(), line);
                }
            }
        } catch (Exception ex) {
            log.error("Error reading AI stream for conversation: {}", conversationId, ex);
        }

        String finalReply = accumulatedContent.toString().trim();
        if (finalReply.isEmpty()) {
            finalReply = FALLBACK_REPLY;
        }

        final String replyToSave = finalReply;
        transactionTemplate.execute(status -> saveAndBroadcastBotMessage(
                conversation.getId(),
                botParticipant.get().getId(),
                replyToSave,
                recommendations
        ));

        if (handoffRecommended) {
            try {
                chatConversationService.requestAgentFromAi(conversation.getId())
                        .ifPresent(response -> log.info(
                                "AI handoff requested for conversation {}, status={}",
                                conversation.getId(),
                                response.getStatus()
                        ));
            } catch (RuntimeException ex) {
                log.warn("Failed to request agent from AI handoff for conversation {}", conversation.getId(), ex);
            }
        }
    }

    private void broadcastChunk(UUID conversationId, UUID botParticipantId, String chunk) {
        ChatMessageResponse chunkResponse = ChatMessageResponse.builder()
                .conversationId(conversationId)
                .participantId(botParticipantId)
                .senderType(ParticipantType.BOT)
                .messageType(ChatMessageType.TEXT_STREAM_CHUNK)
                .content(chunk)
                .recommendedProducts(List.of())
                .createdAt(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, chunkResponse);
    }

    private Optional<ChatMessageResponse> saveAndBroadcastBotMessage(
            UUID conversationId,
            UUID botParticipantId,
            String replyContent
    ) {
        return saveAndBroadcastBotMessage(conversationId, botParticipantId, replyContent, List.of());
    }

    private Optional<ChatMessageResponse> saveAndBroadcastBotMessage(
            UUID conversationId,
            UUID botParticipantId,
            String replyContent,
            List<RecommendationPayload> recommendations
    ) {
        Optional<ChatMessageResponse> responseOpt = saveBotMessage(
                conversationId, botParticipantId, replyContent, recommendations);
        responseOpt.ifPresent(response -> messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response));
        return responseOpt;
    }

    private Optional<ChatMessageResponse> saveBotMessage(
            UUID conversationId,
            UUID botParticipantId,
            String replyContent,
            List<RecommendationPayload> recommendations
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

        List<ChatMessageRecommendation> recommendationEntities = new ArrayList<>();
        for (int index = 0; index < recommendations.size(); index++) {
            RecommendationPayload item = recommendations.get(index);
            recommendationEntities.add(ChatMessageRecommendation.builder()
                    .message(botMessage)
                    .productId(item.productId())
                    .variantId(item.variantId())
                    .name(item.name())
                    .imageKey(item.imageKey())
                    .price(item.price())
                    .originalPrice(item.originalPrice())
                    .salePrice(item.salePrice())
                    .stock(item.stock())
                    .sortOrder(index)
                    .build());
        }
        botMessage.setRecommendedProducts(recommendationEntities);

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
                    buildBusinessContext(conversationId, role, null, null)
            );
        } catch (Exception ex) {
            log.warn("AI service failed for conversation {}", conversationId, ex);
            return Optional.empty();
        }
    }

    private Map<String, Object> buildBusinessContext(
            UUID conversationId,
            Role role,
            UUID accountId,
            Map<String, Object> pageContext
    ) {
        Map<String, Object> context = new HashMap<>();
        context.put("conversationId", conversationId.toString());
        context.put("role", role == null ? Role.CUSTOMER.name() : role.name());
        context.put("generatedAt", Instant.now().toString());
        aiManagementService.generateAiToolAccessToken(accountId)
                .ifPresent(token -> context.put("toolAccessToken", token));

        Map<String, Object> safePageContext = sanitizePageContext(pageContext);
        if (!safePageContext.isEmpty()) {
            context.put("pageContext", safePageContext);
            copyIfPresent(safePageContext, context, "currentProductId");
            copyIfPresent(safePageContext, context, "productName");
            copyIfPresent(safePageContext, context, "route");
        }
        return context;
    }

    private Map<String, Object> sanitizePageContext(Map<String, Object> pageContext) {
        if (pageContext == null || pageContext.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> safe = new HashMap<>();
        copyString(pageContext, safe, "route", 200);
        copyString(pageContext, safe, "currentProductId", 80);
        copyString(pageContext, safe, "productName", 200);
        copyString(pageContext, safe, "productSlug", 200);
        copyString(pageContext, safe, "selectedVariantId", 80);
        return safe;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private void copyString(Map<String, Object> source, Map<String, Object> target, String key, int maxLength) {
        Object value = source.get(key);
        if (!(value instanceof String raw)) {
            return;
        }
        String normalized = normalizeContent(raw);
        if (normalized == null) {
            return;
        }
        target.put(key, normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized);
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

    private record RecommendationPayload(
            UUID productId,
            UUID variantId,
            String name,
            String imageKey,
            BigDecimal price,
            BigDecimal originalPrice,
            BigDecimal salePrice,
            int stock
    ) {
        private static RecommendationPayload from(JsonNode node) {
            try {
                String imageKey = node.path("imageKey").asText("").trim();
                if (imageKey.isEmpty()) {
                    return null;
                }
                String variantId = node.path("variantId").asText("").trim();
                BigDecimal price = node.path("price").decimalValue();
                BigDecimal originalPrice = node.hasNonNull("originalPrice")
                        ? node.path("originalPrice").decimalValue()
                        : price;
                BigDecimal salePrice = node.hasNonNull("salePrice")
                        ? node.path("salePrice").decimalValue()
                        : null;
                return new RecommendationPayload(
                        UUID.fromString(node.path("productId").asText()),
                        variantId.isEmpty() ? null : UUID.fromString(variantId),
                        node.path("name").asText(""),
                        imageKey,
                        price,
                        originalPrice,
                        salePrice,
                        node.path("stock").asInt(0)
                );
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}

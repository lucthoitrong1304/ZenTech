package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.ChatMessageRequest;
import hcmute.edu.zentech.dto.request.TypingEventRequest;
import hcmute.edu.zentech.dto.response.ChatErrorResponse;
import hcmute.edu.zentech.dto.response.TypingEventResponse;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{conversationId}/send")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Valid @Payload ChatMessageRequest request,
            Principal principal
    ) {
        try {
            chatMessageService.sendMessage(
                    conversationId,
                    request,
                    getAccountId(principal)
            );
        } catch (RuntimeException ex) {
            sendError(principal, ex.getMessage());
        }
    }

    // Làm hiệu ứng đang gõ
    @MessageMapping("/chat/{conversationId}/typing")
    public void typing(
            @DestinationVariable UUID conversationId,
            @Payload TypingEventRequest request,
            Principal principal
    ) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/conversations/" + conversationId,
                    TypingEventResponse.builder()
                            .conversationId(conversationId)
                            .accountId(getAccountId(principal))
                            .typing(request.isTyping())
                            .build()
            );
        } catch (RuntimeException ex) {
            sendError(principal, ex.getMessage());
        }
    }

    private UUID getAccountId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new IllegalArgumentException("Authentication is required");
    }

    private void sendError(Principal principal, String message) {
        if (principal == null) {
            return;
        }

        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/chat-errors",
                ChatErrorResponse.builder()
                        .message(message)
                        .timestamp(Instant.now())
                        .build()
        );
    }
}

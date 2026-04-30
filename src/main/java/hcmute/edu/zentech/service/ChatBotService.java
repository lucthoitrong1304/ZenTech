package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ChatBotService {
    public Optional<ChatMessageResponse> handleCustomerMessage(UUID conversationId, ChatMessageResponse message) {
        return Optional.empty();
    }
}

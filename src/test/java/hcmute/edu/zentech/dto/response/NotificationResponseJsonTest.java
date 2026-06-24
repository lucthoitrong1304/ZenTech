package hcmute.edu.zentech.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hcmute.edu.zentech.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializesReadStatusUsingFrontendContract() throws Exception {
        NotificationResponse response = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .title("New message")
                .content("You have a new message")
                .isRead(true)
                .type(NotificationType.CHAT_MESSAGE)
                .referenceId(UUID.randomUUID())
                .createdAt(Instant.parse("2026-06-24T10:00:00Z"))
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("isRead").asBoolean()).isTrue();
        assertThat(json.has("read")).isFalse();
    }
}

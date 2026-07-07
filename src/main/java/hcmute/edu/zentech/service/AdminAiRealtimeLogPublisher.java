package hcmute.edu.zentech.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAiRealtimeLogPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishAiInfo(String message) {
        publishAiLog("INFO", message, message);
    }

    public void publishAiWarn(String message) {
        publishAiLog("WARN", message, message);
    }

    public void publishAiError(String message, Throwable throwable) {
        String details = throwable == null ? message : message + " | " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        publishAiLog("ERROR", message, details);
    }

    private void publishAiLog(String level, String message, String details) {
        try {
            String traceId = MDC.get("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = "ZT-AI-BE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", "WS-AI-BE-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8));
            payload.put("timestamp", Instant.now().toString());
            payload.put("level", level);
            payload.put("category", "AI-SERVICE");
            payload.put("message", message);
            payload.put("details", details);
            payload.put("traceId", traceId);

            messagingTemplate.convertAndSend("/topic/admin.logs", payload);
        } catch (Exception e) {
            log.warn("Failed to publish realtime AI log: {}", e.getMessage());
        }
    }
}
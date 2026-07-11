package hcmute.edu.zentech.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiLogTailerTest {

    @Test
    void systemMarkerIsNotPublishedAsRequestTrace() {
        Map<String, Object> payload = publish("2026-07-11 17:48:47,497 [INFO] [uvicorn.error] [ZT-AI-SYSTEM] - Application startup complete.");

        assertEquals("", payload.get("traceId"));
        assertEquals("AI-SERVICE", payload.get("category"));
    }

    @Test
    void aiRequestTraceIsPreserved() {
        Map<String, Object> payload = publish("2026-07-11 17:51:17,724 [INFO] [ai-service] [ZT-AI-Ab12Cd34] - Incoming Request: POST /management/analyze/report");

        assertEquals("ZT-AI-Ab12Cd34", payload.get("traceId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> publish(String line) {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        AiLogTailer tailer = new AiLogTailer(messagingTemplate);
        tailer.processLogLine(line);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/admin.logs"), payloadCaptor.capture());
        return (Map<String, Object>) payloadCaptor.getValue();
    }
}

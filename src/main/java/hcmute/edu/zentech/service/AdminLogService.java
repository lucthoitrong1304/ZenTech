package hcmute.edu.zentech.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminLogService {

    private final LokiService lokiService;

    // Sử dụng logger riêng biệt đã cấu hình trong logback-spring.xml để ghi log vào frontend.log
    private static final Logger frontendLogger = LoggerFactory.getLogger("frontend-logger");

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    /**
     * Lấy log hệ thống từ Loki
     */
    public List<Map<String, Object>> getLogs(String level, String search, String traceId, int limit) {
        return lokiService.queryLogs(level, search, traceId, limit);
    }

    /**
     * Ghi nhận log lỗi từ Frontend gửi lên vào file frontend.log
     */
    public void writeClientLog(Map<String, Object> logPayload) {
        String traceId = (String) logPayload.getOrDefault("traceId", "");
        String level = (String) logPayload.getOrDefault("level", "ERROR");
        String message = (String) logPayload.getOrDefault("message", "");
        String url = (String) logPayload.getOrDefault("url", "");
        String stackTrace = (String) logPayload.getOrDefault("stackTrace", "");

        // Đưa traceId của Client vào MDC để logback ghi nhận đúng trường [%X{traceId}]
        if (traceId != null && !traceId.trim().isEmpty()) {
            MDC.put("traceId", traceId);
        }

        try {
            // Định dạng dòng ghi log Client
            String formattedLog = String.format("[%s] - Msg: %s | URL: %s | Stack: %s",
                    level.toUpperCase(), message, url, stackTrace);

            // Ghi log qua logger chuyên biệt của frontend
            if (level.equalsIgnoreCase("ERROR")) {
                frontendLogger.error(formattedLog);
            } else if (level.equalsIgnoreCase("WARN")) {
                frontendLogger.warn(formattedLog);
            } else {
                frontendLogger.info(formattedLog);
            }

        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * Gọi AI Service giải thích log lỗi
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> explainLogError(Map<String, Object> payload) {
        try {
            String explainUrl = aiBaseUrl + "/admin/logs/explain";
            log.info("Calling AI service to explain log: {}", explainUrl);

            Map<String, String> requestBody = Map.of(
                    "log_message", (String) payload.getOrDefault("logMessage", ""),
                    "log_details", (String) payload.getOrDefault("logDetails", ""),
                    "service", (String) payload.getOrDefault("service", "")
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(explainUrl, requestBody, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (Map<String, Object>) response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to explain log using AI: {}", e.getMessage());
            return Map.of("explanation", "Không thể liên kết với AI Service để giải thích lỗi này.");
        }
        return Map.of("explanation", "AI Service không phản hồi thông tin phân tích.");
    }
}

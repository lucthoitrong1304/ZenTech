package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class LokiService {

    @Value("${app.loki.url:http://localhost:3100}")
    private String lokiUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // Regex để bóc tách level (INFO/WARN/ERROR/DEBUG) trong câu log
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(INFO|WARN|ERROR|DEBUG)\\b");
    // Regex để bóc tách traceId dạng ZT-xxxxxxx
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?<=\\[|\\s|^)(ZT-(?:FE-)?[A-Fa-f0-9a-zA-Z]{8})(?=\\]|\\s|$)");
    private static final Pattern JSON_STATUS_CODE_PATTERN = Pattern.compile("\"(?:statusCode|status_code|status)\"\\s*:\\s*(\\d{3})");
    private static final Pattern BUSINESS_STATUS_CODE_PATTERN = Pattern.compile("\\b(?:Business error|Validation error|Argument type mismatch|Access denied|Unexpected server error) \\((\\d{3})\\)");
    private static final Pattern RESPONSE_STATUS_CODE_PATTERN = Pattern.compile("\\b(?:Outgoing Response|Response):\\s*(\\d{3})\\b");
    private static final Pattern REQUEST_FAILED_STATUS_CODE_PATTERN = Pattern.compile("\\b(?:FE_FAILED|HttpRequestFailed)[^\\r\\n]*\\b(\\d{3})\\b");

    public List<Map<String, Object>> queryLogs(String level, String search, String traceId, int limit) {
        return queryLogs(level, search, traceId, limit, null, null);
    }

    public List<Map<String, Object>> queryLogs(String level, String search, String traceId, int limit, Long startTimeMs, Long endTimeMs) {
        try {
            // 1. Xây dựng câu truy vấn LogQL
            // Truy vấn cả 3 nguồn: backend, frontend, ai-service
            StringBuilder logql = new StringBuilder("{service=~\"backend|frontend|ai-service\"}");

            // Lọc theo traceId
            if (traceId != null && !traceId.trim().isEmpty()) {
                logql.append(" |= \"").append(traceId.trim()).append("\"");
            }

            // Lọc theo level (chỉ lọc nếu level không phải ALL)
            if (level != null && !level.trim().isEmpty() && !level.equalsIgnoreCase("ALL")) {
                logql.append(" |= \"").append(level.toUpperCase().trim()).append("\"");
            }

            // Lọc theo từ khóa tìm kiếm
            if (search != null && !search.trim().isEmpty()) {
                logql.append(" |= \"").append(search.trim()).append("\"");
            }

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(lokiUrl)
                    .path("/loki/api/v1/query_range")
                    .queryParam("query", logql.toString())
                    .queryParam("limit", limit)
                    .queryParam("direction", "BACKWARD");

            if (startTimeMs != null) {
                long startNanos = startTimeMs * 1_000_000L;
                uriBuilder.queryParam("start", startNanos);
            }

            if (endTimeMs != null) {
                long endNanos = endTimeMs * 1_000_000L;
                uriBuilder.queryParam("end", endNanos);
            }

            java.net.URI queryUri = uriBuilder.build().toUri();

            log.info("Querying Loki URI: {}", queryUri);

            ResponseEntity<String> response = restTemplate.getForEntity(queryUri, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Loki query failed with status code: {}", response.getStatusCode());
                return Collections.emptyList();
            }

            return parseLokiResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error querying logs from Loki (Loki might be offline): {}", e.getMessage());
            // Trả về một dòng cảnh báo Loki offline thay vì quăng lỗi crash trang
            return List.of(Map.of(
                    "id", "OFFLINE-LOG",
                    "timestamp", Instant.now(),
                    "level", "WARN",
                    "category", "SYSTEM",
                    "message", "Không thể kết nối đến dịch vụ Loki. Vui lòng kiểm tra Docker Container zentech-loki.",
                    "details", "Lỗi chi tiết: " + e.getMessage()
            ));
        }
    }

    private List<Map<String, Object>> parseLokiResponse(String responseBody) {
        List<Map<String, Object>> parsedLogs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode streams = root.path("data").path("result");

            if (!streams.isArray()) {
                return parsedLogs;
            }

            for (JsonNode streamNode : streams) {
                // Lấy thông tin nhãn service
                String service = streamNode.path("stream").path("service").asText("unknown").toUpperCase();
                JsonNode values = streamNode.path("values");

                if (!values.isArray()) {
                    continue;
                }

                for (JsonNode logEntry : values) {
                    if (logEntry.size() < 2) {
                        continue;
                    }

                    // Loki trả về dạng: [ "timestamp_nanoseconds", "raw_log_line" ]
                    String timestampNsStr = logEntry.get(0).asText();
                    String rawLogLine = logEntry.get(1).asText();

                    long timestampMs = Long.parseLong(timestampNsStr.substring(0, 13));
                    Instant timestamp = Instant.ofEpochMilli(timestampMs);

                    parsedLogs.add(formatLogLine(timestampNsStr, timestamp, service, rawLogLine));
                }
            }

            // Sắp xếp lại danh sách log theo thời gian giảm dần (mới nhất lên đầu)
            parsedLogs.sort((a, b) -> {
                String tsA = (String) a.get("id");
                String tsB = (String) b.get("id");
                return tsB.compareTo(tsA);
            });

        } catch (Exception e) {
            log.error("Error parsing Loki JSON response: ", e);
        }
        return parsedLogs;
    }

    private Map<String, Object> formatLogLine(String id, Instant timestamp, String service, String rawLine) {
        String cleanMessage = rawLine.trim();
        String level = "INFO";
        String traceId = "";

        // Trích xuất level từ dòng log
        Matcher levelMatcher = LEVEL_PATTERN.matcher(rawLine);
        if (levelMatcher.find()) {
            level = levelMatcher.group(1);
        }

        // Trích xuất traceId từ dòng log
        Matcher traceMatcher = TRACE_ID_PATTERN.matcher(rawLine);
        if (traceMatcher.find()) {
            traceId = traceMatcher.group(1);
        }

        // Làm sạch message hiển thị chính: lấy phần nội dung sau dấu "-" đầu tiên nếu có để hiển thị gọn
        int hyphenIndex = rawLine.indexOf(" - ");
        if (hyphenIndex != -1 && hyphenIndex + 3 < rawLine.length()) {
            cleanMessage = rawLine.substring(hyphenIndex + 3).trim();
        }

        Integer statusCode = extractStatusCode(rawLine);

        Map<String, Object> logMap = new LinkedHashMap<>();
        logMap.put("id", id);
        logMap.put("timestamp", timestamp);
        logMap.put("level", level);
        logMap.put("category", service);
        logMap.put("message", cleanMessage);
        logMap.put("details", rawLine);
        logMap.put("traceId", traceId);
        if (statusCode != null) {
            logMap.put("statusCode", statusCode);
        }
        return logMap;
    }

    private Integer extractStatusCode(String rawLine) {
        Matcher jsonMatcher = JSON_STATUS_CODE_PATTERN.matcher(rawLine);
        if (jsonMatcher.find()) {
            return parseStatusCode(jsonMatcher.group(1));
        }

        Matcher businessMatcher = BUSINESS_STATUS_CODE_PATTERN.matcher(rawLine);
        if (businessMatcher.find()) {
            return parseStatusCode(businessMatcher.group(1));
        }

        Matcher responseMatcher = RESPONSE_STATUS_CODE_PATTERN.matcher(rawLine);
        if (responseMatcher.find()) {
            return parseStatusCode(responseMatcher.group(1));
        }

        Matcher failedMatcher = REQUEST_FAILED_STATUS_CODE_PATTERN.matcher(rawLine);
        if (failedMatcher.find()) {
            return parseStatusCode(failedMatcher.group(1));
        }

        return null;
    }

    private Integer parseStatusCode(String value) {
        try {
            int statusCode = Integer.parseInt(value);
            return statusCode >= 100 && statusCode <= 599 ? statusCode : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
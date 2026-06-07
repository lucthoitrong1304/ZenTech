package hcmute.edu.zentech.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiLogTailer {

    private final SimpMessagingTemplate messagingTemplate;
    private Thread tailerThread;
    private volatile boolean running = true;

    // Regex match: 2026-06-07 15:44:30,775 [INFO] [ai-service] [ZT-AI-12345678] - Incoming Request: POST /chat/respond
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s+\\[(INFO|WARN|ERROR|DEBUG)\\]\\s+\\[([^\\]]+)\\]\\s+\\[([^\\]]*)\\]\\s+-\\s+(.*)$"
    );

    @PostConstruct
    public void startTailing() {
        log.info("[AiLogTailer] Starting tailer thread for ai.log");
        tailerThread = new Thread(this::tailLogFile, "AiLogTailer-Thread");
        tailerThread.setDaemon(true);
        tailerThread.start();
    }

    @PreDestroy
    public void stopTailing() {
        log.info("[AiLogTailer] Stopping tailer thread");
        running = false;
        if (tailerThread != null) {
            tailerThread.interrupt();
        }
    }

    private void tailLogFile() {
        File logFile = new File("../docker/logs/ai.log");
        long lastKnownPosition = 0;

        // Wait until log file exists
        while (running && !logFile.exists()) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Only stream new logs, ignore logs written before start
        if (logFile.exists()) {
            lastKnownPosition = logFile.length();
        }

        while (running) {
            try {
                long fileLength = logFile.length();
                if (fileLength < lastKnownPosition) {
                    log.info("[AiLogTailer] Log file truncated or rotated. Resetting position.");
                    lastKnownPosition = 0;
                }

                if (fileLength > lastKnownPosition) {
                    try (RandomAccessFile reader = new RandomAccessFile(logFile, "r")) {
                        reader.seek(lastKnownPosition);
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Convert reader default ISO-8859-1 encoding to UTF-8
                            String utf8Line = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                            processLogLine(utf8Line);
                        }
                        lastKnownPosition = reader.getFilePointer();
                    }
                }
            } catch (Exception e) {
                log.error("[AiLogTailer] Error reading ai.log: {}", e.getMessage());
            }

            try {
                Thread.sleep(500); // Check for changes every 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processLogLine(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return;
        }

        try {
            Matcher matcher = LOG_LINE_PATTERN.matcher(rawLine.trim());
            String timestampStr = Instant.now().toString();
            String level = "INFO";
            String category = "AI-SERVICE";
            String traceId = "";
            String message = rawLine;

            if (matcher.matches()) {
                String rawTime = matcher.group(1).replace(",", "."); // format to match ISO parse
                try {
                    String isoTime = rawTime.replace(" ", "T") + "Z";
                    timestampStr = Instant.parse(isoTime).toString();
                } catch (Exception ignored) {
                    timestampStr = Instant.now().toString();
                }

                level = matcher.group(2);
                category = "AI-SERVICE";
                traceId = matcher.group(4);
                message = matcher.group(5);
            }

            Map<String, Object> logPayload = new HashMap<>();
            String randomSuffix = String.valueOf((int)(Math.random() * 9000) + 1000);
            logPayload.put("id", "WS-AI-" + Instant.now().toEpochMilli() + "-" + randomSuffix);
            logPayload.put("timestamp", timestampStr);
            logPayload.put("level", level);
            logPayload.put("category", category);
            logPayload.put("message", message);
            logPayload.put("details", rawLine);
            logPayload.put("traceId", traceId);

            messagingTemplate.convertAndSend("/topic/admin.logs", logPayload);
        } catch (Exception e) {
            log.error("[AiLogTailer] Error processing log line: {}", e.getMessage());
        }
    }
}

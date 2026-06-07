package hcmute.edu.zentech.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private final SimpMessagingTemplate messagingTemplate;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebSocketLogAppender.class);

    @PostConstruct
    public void registerAppender() {
        System.out.println("[WebSocketLogAppender] registerAppender() called! Registering to Logback Root Logger and frontend-logger.");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Đăng ký cho Root Logger (bao gồm log của backend)
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        this.setContext(context);
        this.setName("WEBSOCKET_APPENDER");
        this.start();
        rootLogger.addAppender(this);

        // Đăng ký cho frontend-logger để bắt log client đẩy lên
        Logger frontendLogger = context.getLogger("frontend-logger");
        if (frontendLogger != null) {
            System.out.println("[WebSocketLogAppender] Registered to frontend-logger.");
            frontendLogger.addAppender(this);
        } else {
            System.err.println("[WebSocketLogAppender] frontend-logger NOT found!");
        }
    }

    @jakarta.annotation.PreDestroy
    public void stopAppender() {
        System.out.println("[WebSocketLogAppender] stopAppender() called! Unregistering from Logback.");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (rootLogger != null) {
            rootLogger.detachAppender(this);
        }
        Logger frontendLogger = context.getLogger("frontend-logger");
        if (frontendLogger != null) {
            frontendLogger.detachAppender(this);
        }
        this.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        try {
            String loggerName = event.getLoggerName() != null ? event.getLoggerName() : "";
            
            // Tránh ghi đè chính log gửi websocket vào vòng lặp vô hạn (đệ quy)
            if (loggerName.isEmpty() ||
                loggerName.contains("WebSocketLogAppender") || 
                loggerName.contains("SimpMessagingTemplate") ||
                loggerName.contains("wstx") ||
                loggerName.contains("org.springframework.messaging") ||
                loggerName.contains("org.springframework.web.socket") ||
                loggerName.contains("org.springframework.amqp") ||
                loggerName.contains("com.rabbitmq") ||
                loggerName.contains("reactor.netty") ||
                loggerName.contains("io.netty")) {
                return;
            }

            String level = event.getLevel() != null ? event.getLevel().toString() : "INFO";
            
            String traceId = "";
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null) {
                traceId = mdc.getOrDefault("traceId", "");
                if (traceId == null) {
                    traceId = "";
                }
            }

            String message = event.getFormattedMessage() != null ? event.getFormattedMessage() : "";
            
            // Log target endpoint exclusions
            if (message.contains("/ws") || message.contains("/api/health")) {
                return;
            }
            
            String category = "BACKEND";

            // Nhận biết log từ frontend gửi lên và dọn dẹp hiển thị
            if ("frontend-logger".equals(loggerName)) {
                category = "FRONTEND";
                if (message.contains(" - Msg: ")) {
                    int msgIndex = message.indexOf(" - Msg: ");
                    int urlIndex = message.indexOf(" | URL: ");
                    if (urlIndex != -1 && msgIndex + 8 < urlIndex) {
                        message = message.substring(msgIndex + 8, urlIndex);
                    }
                }
            }

            // Dùng HashMap thay vì Map.of để tránh lỗi NullPointerException
            Map<String, Object> logPayload = new java.util.HashMap<>();
            String randomSuffix = String.valueOf((int)(Math.random() * 9000) + 1000);
            logPayload.put("id", "WS-" + Instant.ofEpochMilli(event.getTimeStamp()).toString() + "-" + randomSuffix);
            logPayload.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            logPayload.put("level", level);
            logPayload.put("category", category);
            logPayload.put("message", message);
            logPayload.put("details", String.format("%s [%s] %s - %s", 
                Instant.ofEpochMilli(event.getTimeStamp()).toString(), 
                level, 
                loggerName, 
                event.getFormattedMessage() != null ? event.getFormattedMessage() : ""));
            logPayload.put("traceId", traceId);

            System.out.println("[WebSocketLogAppender] Sending log to WS: " + message);
            messagingTemplate.convertAndSend("/topic/admin.logs", logPayload);
        } catch (org.springframework.messaging.MessageDeliveryException e) {
            logger.error("[WebSocketLogAppender] MessageDeliveryException occurred: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("[WebSocketLogAppender] Exception occurred while appending log: ", e);
        }
    }
}

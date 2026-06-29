package hcmute.edu.zentech.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        // 1. Lấy traceId từ Header, nếu không có thì sinh mới dạng ZT-xxxxxxx
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = "ZT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        // 2. Gán traceId vào MDC của Logback để tất cả các dòng log của request này tự động in ra traceId
        MDC.put(MDC_TRACE_ID_KEY, traceId);

        // 3. Gán traceId vào HTTP Response Header để Client dễ dàng kiểm tra và map thông tin
        response.setHeader(TRACE_ID_HEADER, traceId);

        String uri = request.getRequestURI();
        String method = request.getMethod();
        
        // Bỏ qua ghi log cho endpoint đẩy log client, websocket và health để tránh rác log file
        boolean shouldLog = !isInternalObservabilityEndpoint(uri);

        if (shouldLog) {
            log.info("Incoming Request: {} {} từ IP: {}", method, uri, request.getRemoteAddr());
        }

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            if (shouldLog) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Request failed: {} {} - Lỗi: {} - Thời gian xử lý: {}ms", method, uri, ex.getMessage(), duration);
            }
            throw ex;
        } finally {
            if (shouldLog) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("Outgoing Response: {} cho {} {} - Thời gian xử lý: {}ms", response.getStatus(), method, uri, duration);
            }
            // 4. Xóa traceId khỏi MDC sau khi kết thúc request để tránh rò rỉ bộ nhớ sang request khác
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
    private boolean isInternalObservabilityEndpoint(String uri) {
        return uri == null
                || uri.contains("/api/logs/client")
                || uri.startsWith("/ws")
                || uri.equals("/health")
                || uri.startsWith("/api/health")
                || uri.startsWith("/api/admin/logs")
                || uri.startsWith("/api/admin/activity-logs")
                || uri.startsWith("/api/admin/incidents/issue-links")
                || uri.startsWith("/actuator");
    }
}

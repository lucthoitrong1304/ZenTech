package hcmute.edu.zentech.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
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

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 4. Xóa traceId khỏi MDC sau khi kết thúc request để tránh rò rỉ bộ nhớ sang request khác
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
}

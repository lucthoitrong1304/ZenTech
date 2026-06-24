package hcmute.edu.zentech.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class PrometheusScrapeTokenFilter extends OncePerRequestFilter {
    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

    @Value("${app.prometheus.scrape-token}")
    private String scrapeToken;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !PROMETHEUS_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String providedToken = StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : "";

        if (!constantTimeEquals(scrapeToken, providedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Prometheus scrape token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}

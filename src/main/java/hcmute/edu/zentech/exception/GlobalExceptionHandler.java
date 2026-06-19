package hcmute.edu.zentech.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.RequiredArgsConstructor;
import hcmute.edu.zentech.service.AdminIncidentService;
import hcmute.edu.zentech.security.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AdminIncidentService adminIncidentService;

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String error, String message) {
        return buildErrorResponse(status, error, message, List.of());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            List<String> errors
    ) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", status.value());
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        errorResponse.put("errors", errors);

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidationException(Exception ex) {
        List<String> errors = new ArrayList<>();

        if (ex instanceof BindException bindException) {
            bindException.getBindingResult().getFieldErrors().forEach(fieldError ->
                    errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage())
            );
        } else if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            methodArgumentNotValidException.getBindingResult().getFieldErrors().forEach(fieldError ->
                    errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage())
            );
        }

        log.warn("Validation error (400): {}", errors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid request data", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid value '%s' for field '%s'", ex.getValue(), ex.getName());
        log.warn("Argument type mismatch (400): {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied (403): {}", ex.getMessage());

        String apiPath = request.getRequestURI();
        if (apiPath != null && apiPath.startsWith("/api/admin/")) {
            String traceId = MDC.get("traceId");
            String httpMethod = request.getMethod();
            java.util.UUID userId = null;
            try {
                userId = SecurityContextUtils.getCurrentUserId();
            } catch (Exception e) {
                // Unauthenticated
            }
            try {
                // Chỉ tự sinh sự cố đối với lỗi 403 ở trang Admin, loại trừ các API liên quan đến chính incidents/tickets để tránh loop
                if (!apiPath.startsWith("/api/admin/incidents") && !apiPath.startsWith("/api/admin/tickets")) {
                    adminIncidentService.createIncidentFromException(ex, traceId, apiPath, httpMethod, 403, "backend", userId);
                }
            } catch (Exception e) {
                log.error("Failed to automatically record 403 incident: {}", e.getMessage());
            }
        }

        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.warn("Business error (400): {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found (404): {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler({
            IllegalStateException.class,
            NullPointerException.class,
            UnsupportedOperationException.class
    })
    public ResponseEntity<Map<String, Object>> handleSystemRuntimeExceptions(RuntimeException ex, HttpServletRequest request) {
        return handleGlobalException(ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex, HttpServletRequest request) {
        // Kiểm tra nếu là lỗi do client ngắt kết nối (ClientAbortException hoặc IOException broken pipe)
        boolean isClientAbort = false;
        Throwable cause = ex;
        while (cause != null) {
            String className = cause.getClass().getName();
            if (className.contains("ClientAbortException") || 
                (cause instanceof java.io.IOException && 
                 (cause.getMessage() != null && 
                  (cause.getMessage().toLowerCase().contains("broken pipe") || 
                   cause.getMessage().toLowerCase().contains("connection was aborted") || 
                   cause.getMessage().toLowerCase().contains("established connection was aborted"))))) {
                isClientAbort = true;
                break;
            }
            cause = cause.getCause();
        }

        if (isClientAbort) {
            log.warn("Client aborted connection (IOException/ClientAbortException) for path: {}", request.getRequestURI());
        } else {
            log.error("Unexpected server error (500): ", ex);
        }

        String traceId = MDC.get("traceId");
        String apiPath = request.getRequestURI();
        String httpMethod = request.getMethod();
        java.util.UUID userId = null;
        try {
            userId = SecurityContextUtils.getCurrentUserId();
        } catch (Exception e) {
            // Unauthenticated
        }

        int statusCode = 500;
        Throwable rootCause = ex;
        while (rootCause != null) {
            String className = rootCause.getClass().getName();
            if (rootCause instanceof java.net.ConnectException 
                || rootCause instanceof java.net.SocketTimeoutException 
                || className.contains("ResourceAccessException")
                || className.contains("WebClientResponseException")
                || className.contains("HttpServerErrorException")
                || className.contains("HttpClientErrorException")) {
                statusCode = 502; // Bad Gateway / Integration Error
                break;
            }
            rootCause = rootCause.getCause();
        }

        try {
            // Ngăn chặn vòng lặp vô hạn (Infinite Loop): Không tự sinh sự cố đối với các lỗi phát sinh
            // từ chính các API quản lý sự cố và quản lý ticket ở trang quản trị (Admin Dashboard).
            // Đồng thời không tạo sự cố cho các lỗi ngắt kết nối từ phía client.
            if (!isClientAbort && apiPath != null && !apiPath.startsWith("/api/admin/incidents") && !apiPath.startsWith("/api/admin/tickets")) {
                adminIncidentService.createIncidentFromException(ex, traceId, apiPath, httpMethod, statusCode, "backend", userId);
            }
        } catch (Exception e) {
            log.error("Failed to automatically record incident: {}", e.getMessage());
        }

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected server error occurred. Please try again later."
        );
    }
}

package hcmute.edu.zentech.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.service.AdminActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityLogAspect {

    private final AdminActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    @Pointcut("@annotation(hcmute.edu.zentech.aspect.TrackActivity)")
    public void trackedActivityMethods() {}

    @AfterReturning(pointcut = "trackedActivityMethods()", returning = "result")
    public void logActivity(JoinPoint joinPoint, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            TrackActivity trackActivity = findTrackActivity(joinPoint, signature.getMethod());
            if (trackActivity == null) {
                return;
            }
            writeActivity(joinPoint, result, trackActivity, trackActivity.action(), trackActivity.summary(), null);
        } catch (Exception e) {
            log.error("Failed to log tracked activity: {}", e.getMessage(), e);
        }
    }

    @AfterThrowing(pointcut = "trackedActivityMethods()", throwing = "throwable")
    public void logFailedActivity(JoinPoint joinPoint, Throwable throwable) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            TrackActivity trackActivity = findTrackActivity(joinPoint, signature.getMethod());
            if (trackActivity == null || !trackActivity.logOnFailure()) {
                return;
            }
            String summary = firstNonBlank(trackActivity.summary(), trackActivity.failureAction().name() + " failed")
                    + " - " + throwable.getClass().getSimpleName();
            writeActivity(joinPoint, null, trackActivity, trackActivity.failureAction(), summary, throwable);
        } catch (Exception e) {
            log.error("Failed to log failed tracked activity: {}", e.getMessage(), e);
        }
    }

    private void writeActivity(
            JoinPoint joinPoint,
            Object result,
            TrackActivity trackActivity,
            ActivityAction action,
            String configuredSummary,
            Throwable throwable
    ) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            HttpServletRequest request = getCurrentRequest();
            String requestUri = request != null ? request.getRequestURI() : "";
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            String targetId = "";
            Map<String, Object> metadata = new LinkedHashMap<>();

            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg == null || isFrameworkClass(arg)) {
                        continue;
                    }

                    String paramName = (parameterNames != null && parameterNames.length > i) ? parameterNames[i] : "";
                    String className = arg.getClass().getSimpleName();
                    String loweredParam = paramName.toLowerCase();
                    String loweredClass = className.toLowerCase();

                    if (arg instanceof UUID
                            || loweredParam.equals("id")
                            || loweredParam.endsWith("id")
                            || loweredParam.contains("id")
                            || loweredParam.contains("code")) {
                        targetId = arg.toString();
                    }

                    if (loweredParam.contains("request")
                            || loweredParam.contains("dto")
                            || loweredParam.contains("payload")
                            || loweredClass.contains("request")
                            || loweredClass.contains("dto")
                            || loweredClass.contains("payload")) {
                        metadata.put(paramName.isBlank() ? className : paramName, sanitizeObject(arg));
                    }
                }
            }

            if (throwable != null) {
                metadata.put("errorType", throwable.getClass().getSimpleName());
                metadata.put("errorMessage", sanitizeText(throwable.getMessage()));
            }

            AuthResponse authResponse = extractAuthResponse(result);
            ActivityArea effectiveArea = resolveArea(trackActivity.area(), authResponse);
            String targetType = firstNonBlank(trackActivity.targetType(), determineTargetTypeFromUri(requestUri));
            String targetLabel = firstNonBlank(resolveAuthTargetLabel(authResponse), targetId, targetType);
            String summary = buildSummary(action, configuredSummary, effectiveArea.name(), targetType, targetLabel);
            String metadataJson = metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
            UUID loggedInUserId = resolveUserId(action, authResponse);

            activityLogService.log(
                    loggedInUserId,
                    effectiveArea,
                    trackActivity.module(),
                    action,
                    trackActivity.severity(),
                    targetType,
                    targetId,
                    targetLabel,
                    summary,
                    metadataJson
            );
        } catch (Exception e) {
            log.error("Failed to write structured activity: {}", e.getMessage(), e);
        }
    }

    private AuthResponse extractAuthResponse(Object result) {
        if (result == null) {
            return null;
        }
        Object body = result;
        if (result instanceof ResponseEntity<?> responseEntity) {
            body = responseEntity.getBody();
        }
        return body instanceof AuthResponse authResponse ? authResponse : null;
    }

    private UUID resolveUserId(ActivityAction action, AuthResponse authResponse) {
        if (action != ActivityAction.LOGIN || authResponse == null || authResponse.getAccountId() == null) {
            return null;
        }
        try {
            return UUID.fromString(authResponse.getAccountId());
        } catch (Exception e) {
            log.warn("Cannot parse accountId as UUID: {}", authResponse.getAccountId());
            return null;
        }
    }

    private ActivityArea resolveArea(ActivityArea configuredArea, AuthResponse authResponse) {
        if (authResponse == null || authResponse.getRoles() == null || authResponse.getRoles().isEmpty()) {
            return configuredArea;
        }
        boolean isAdmin = authResponse.getRoles().stream().anyMatch(role -> normalizeRole(role).equals("ADMIN"));
        if (isAdmin) {
            return ActivityArea.ADMIN;
        }
        boolean isManagement = authResponse.getRoles().stream()
                .map(this::normalizeRole)
                .anyMatch(role -> role.equals("OWNER") || role.equals("MANAGER") || role.equals("EMPLOYEE"));
        if (isManagement) {
            return ActivityArea.MANAGEMENT;
        }
        boolean isCustomer = authResponse.getRoles().stream().anyMatch(role -> normalizeRole(role).equals("CUSTOMER"));
        return isCustomer ? ActivityArea.CUSTOMER : configuredArea;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.replace("ROLE_", "").trim().toUpperCase();
    }

    private String resolveAuthTargetLabel(AuthResponse authResponse) {
        if (authResponse == null) {
            return "";
        }
        return firstNonBlank(authResponse.getEmail(), authResponse.getFullName(), authResponse.getAccountId());
    }

    private TrackActivity findTrackActivity(JoinPoint joinPoint, Method method) {
        TrackActivity trackActivity = method.getAnnotation(TrackActivity.class);
        if (trackActivity != null) {
            return trackActivity;
        }
        try {
            Method targetMethod = joinPoint.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            return targetMethod.getAnnotation(TrackActivity.class);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest();
            }
        } catch (Exception e) {
            log.debug("Not in an HTTP request context: {}", e.getMessage());
        }
        return null;
    }

    private String determineTargetTypeFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "SYSTEM";
        }
        String cleanUri = uri.toLowerCase();
        if (cleanUri.contains("/inventory")) return "INVENTORY";
        if (cleanUri.contains("/products")) return "PRODUCT";
        if (cleanUri.contains("/categories")) return "CATEGORY";
        if (cleanUri.contains("/orders")) return "ORDER";
        if (cleanUri.contains("/accounts")) return "ACCOUNT";
        if (cleanUri.contains("/coupons")) return "COUPON";
        if (cleanUri.contains("/vouchers")) return "VOUCHER";
        if (cleanUri.contains("/auth")) return "AUTH";
        if (cleanUri.contains("/payments")) return "PAYMENT";
        if (cleanUri.contains("/logs")) return "LOG";
        return "SYSTEM";
    }

    private boolean isFrameworkClass(Object arg) {
        if (arg == null) return true;
        String name = arg.getClass().getName();
        return name.startsWith("jakarta.servlet")
                || name.startsWith("org.springframework")
                || name.startsWith("java.")
                || arg instanceof org.springframework.validation.BindingResult
                || arg instanceof org.springframework.web.multipart.MultipartFile;
    }

    private Object sanitizeObject(Object value) {
        try {
            JsonNode node = objectMapper.valueToTree(value);
            return sanitizeNode(node);
        } catch (Exception e) {
            return sanitizeText(String.valueOf(value));
        }
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode objectNode = ((ObjectNode) node).deepCopy();
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode current = objectNode.get(fieldName);
                if (isSensitiveKey(fieldName)) {
                    objectNode.set(fieldName, TextNode.valueOf("[MASKED]"));
                } else {
                    objectNode.set(fieldName, sanitizeNode(current));
                }
            });
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(item -> arrayNode.add(sanitizeNode(item)));
            return arrayNode;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(sanitizeText(node.asText()));
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase();
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("refresh")
                || normalized.contains("access")
                || normalized.contains("secret")
                || normalized.contains("credential")
                || normalized.contains("otp")
                || normalized.contains("authorization");
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll(
                "(?i)(password|token|refresh|access|secret|credential|otp|authorization)([\\w.-]*)(\\s*[=:]\\s*)([^,\\s}\\\"]+)",
                "$1$2$3[MASKED]"
        );
    }

    private String buildSummary(ActivityAction action, String configuredSummary, String area, String targetType, String targetId) {
        if (configuredSummary != null && !configuredSummary.isBlank()) {
            return configuredSummary;
        }
        String target = firstNonBlank(targetId, targetType, "SYSTEM");
        return area + " " + action.name().replace('_', ' ') + " tren " + target;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

package hcmute.edu.zentech.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import hcmute.edu.zentech.dto.request.InventoryAdjustmentRequest;
import hcmute.edu.zentech.dto.request.UpdateAccountStatusRequest;
import hcmute.edu.zentech.dto.request.UpdateCustomerStatusRequest;
import hcmute.edu.zentech.dto.response.AiAgentResponse;
import hcmute.edu.zentech.dto.response.AiDatasetResponse;
import hcmute.edu.zentech.dto.response.AiDocumentResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.ProductGroupResponse;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CouponRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import hcmute.edu.zentech.service.AdminActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityLogAspect {

    private final AdminActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final AccountUserRepository accountUserRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductGroupRepository productGroupRepository;
    private final CouponRepository couponRepository;
    private final ProductVariantRepository productVariantRepository;

    @Pointcut("@annotation(hcmute.edu.zentech.aspect.TrackActivity)")
    public void trackedActivityMethods() {}

    @Around("trackedActivityMethods()")
    public Object logActivity(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        TrackActivity trackActivity = findTrackActivity(joinPoint, signature.getMethod());
        if (trackActivity == null) {
            return joinPoint.proceed();
        }

        ActivityAction action = resolveRequestAction(trackActivity.action(), joinPoint.getArgs());
        String configuredSummary = resolveRequestSummary(trackActivity.summary(), action);
        String targetId = extractTargetId(signature.getParameterNames(), joinPoint.getArgs());
        Map<String, Object> beforeSnapshot = captureSnapshot(trackActivity.targetType(), action, targetId, joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            Map<String, Object> afterSnapshot = captureSnapshot(trackActivity.targetType(), action, targetId, joinPoint.getArgs());
            writeActivity(joinPoint, result, trackActivity, action, configuredSummary, null, beforeSnapshot, afterSnapshot);
            return result;
        } catch (Throwable throwable) {
            if (trackActivity.logOnFailure()) {
                String summary = firstNonBlank(trackActivity.summary(), trackActivity.failureAction().name() + " failed")
                        + " - " + throwable.getClass().getSimpleName();
                writeActivity(joinPoint, null, trackActivity, trackActivity.failureAction(), summary, throwable, beforeSnapshot, null);
            }
            throw throwable;
        }
    }

    private void writeActivity(
            JoinPoint joinPoint,
            Object result,
            TrackActivity trackActivity,
            ActivityAction action,
            String configuredSummary,
            Throwable throwable,
            Map<String, Object> beforeSnapshot,
            Map<String, Object> afterSnapshot
    ) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            HttpServletRequest request = getCurrentRequest();
            String requestUri = request != null ? request.getRequestURI() : "";
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            String targetId = extractTargetId(parameterNames, args);
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
            appendDiffMetadata(metadata, beforeSnapshot, afterSnapshot, action);

            AuthResponse authResponse = extractAuthResponse(result);
            ActivityArea effectiveArea = resolveArea(trackActivity.area(), authResponse);
            String targetType = firstNonBlank(trackActivity.targetType(), determineTargetTypeFromUri(requestUri));
            String targetLabel = extractTargetLabel(result, targetId, targetType);
            String summary = buildSummary(action, configuredSummary, effectiveArea.name(), targetType, targetLabel);
            String metadataJson = metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
            UUID loggedInUserId = resolveUserId(action, authResponse);
            ActivitySeverity effectiveSeverity = resolveSeverity(trackActivity.severity(), action, throwable);

            activityLogService.log(
                    loggedInUserId,
                    effectiveArea,
                    trackActivity.module(),
                    action,
                    effectiveSeverity,
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

    private ActivityAction resolveRequestAction(ActivityAction defaultAction, Object[] args) {
        if (args == null) {
            return defaultAction;
        }
        for (Object arg : args) {
            if (arg instanceof UpdateCustomerStatusRequest customerStatusReq) {
                return Boolean.FALSE.equals(customerStatusReq.getActive())
                        ? ActivityAction.LOCK_ACCOUNT
                        : ActivityAction.UNLOCK_ACCOUNT;
            }
            if (arg instanceof UpdateAccountStatusRequest accountStatusReq) {
                return Boolean.FALSE.equals(accountStatusReq.getActive())
                        ? ActivityAction.LOCK_ACCOUNT
                        : ActivityAction.UNLOCK_ACCOUNT;
            }
        }
        return defaultAction;
    }

    private String resolveRequestSummary(String defaultSummary, ActivityAction action) {
        if (action == ActivityAction.LOCK_ACCOUNT) {
            return "Khoa tai khoan";
        }
        if (action == ActivityAction.UNLOCK_ACCOUNT) {
            return "Mo khoa tai khoan";
        }
        return defaultSummary;
    }

    private ActivitySeverity resolveSeverity(ActivitySeverity configuredSeverity, ActivityAction action, Throwable throwable) {
        if (action == ActivityAction.LOGIN_FAILED || action == ActivityAction.ACCESS_DENIED) {
            return ActivitySeverity.SECURITY;
        }
        if (action == ActivityAction.CHECKOUT_FAILED || action == ActivityAction.PAYMENT_FAILED) {
            return ActivitySeverity.IMPORTANT;
        }
        if (throwable != null && configuredSeverity == ActivitySeverity.INFO) {
            return ActivitySeverity.IMPORTANT;
        }
        return configuredSeverity != null ? configuredSeverity : ActivitySeverity.INFO;
    }

    private String extractTargetId(String[] parameterNames, Object[] args) {
        if (args == null) {
            return "";
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || isFrameworkClass(arg)) {
                continue;
            }
            String paramName = (parameterNames != null && parameterNames.length > i) ? parameterNames[i] : "";
            String loweredParam = paramName.toLowerCase();
            if (arg instanceof UUID
                    || loweredParam.equals("id")
                    || loweredParam.endsWith("id")
                    || loweredParam.contains("id")
                    || loweredParam.contains("code")) {
                return arg.toString();
            }
        }
        UUID inventoryVariantId = findInventoryVariantId(args);
        return inventoryVariantId != null ? inventoryVariantId.toString() : "";
    }

    private UUID findInventoryVariantId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof InventoryAdjustmentRequest request) {
                return request.getProductVariantId();
            }
        }
        return null;
    }

    private Map<String, Object> captureSnapshot(String targetType, ActivityAction action, String targetId, Object[] args) {
        UUID id = parseUuid(targetId);
        if (action == ActivityAction.UPDATE_STOCK) {
            UUID variantId = id != null ? id : findInventoryVariantId(args);
            return snapshotVariant(variantId);
        }
        if (id == null) {
            return Map.of();
        }
        String normalizedTarget = targetType == null ? "" : targetType.trim().toUpperCase();
        return switch (normalizedTarget) {
            case "ACCOUNT", "CUSTOMER" -> snapshotAccount(id);
            case "ORDER" -> snapshotOrder(id);
            case "PRODUCT" -> snapshotProduct(id);
            case "PRODUCT_GROUP" -> snapshotProductGroup(id);
            case "COUPON" -> snapshotCoupon(id);
            default -> Map.of();
        };
    }

    private Map<String, Object> snapshotAccount(UUID id) {
        return accountUserRepository.findById(id)
                .map(account -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("email", account.getEmail());
                    snapshot.put("role", account.getRole());
                    snapshot.put("active", account.isActive());
                    snapshot.put("passwordSet", account.isPasswordSet());
                    return snapshot;
                })
                .orElse(Map.of());
    }

    private Map<String, Object> snapshotOrder(UUID id) {
        return orderRepository.findById(id)
                .map(order -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("orderStatus", order.getOrderStatus());
                    snapshot.put("paymentStatus", order.getPaymentStatus());
                    snapshot.put("finalPrice", order.getFinalPrice());
                    snapshot.put("discountAmount", order.getDiscountAmount());
                    snapshot.put("shippingFee", order.getShippingFee());
                    snapshot.put("customerId", order.getCustomer() != null ? order.getCustomer().getId() : null);
                    return snapshot;
                })
                .orElse(Map.of());
    }

    private Map<String, Object> snapshotProduct(UUID id) {
        return productRepository.findById(id)
                .map(product -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("productName", product.getProductName());
                    snapshot.put("representativeImageKey", product.getRepresentativeImageKey());
                    snapshot.put("groupId", product.getProductGroup() != null ? product.getProductGroup().getId() : null);
                    snapshot.put("groupName", product.getProductGroup() != null ? product.getProductGroup().getGroupName() : null);
                    snapshot.put("deleted", product.isDeleted());
                    return snapshot;
                })
                .orElse(Map.of("deleted", true));
    }

    private Map<String, Object> snapshotProductGroup(UUID id) {
        return productGroupRepository.findById(id)
                .map(group -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("groupName", group.getGroupName());
                    snapshot.put("description", group.getDescription());
                    snapshot.put("deleted", group.isDeleted());
                    return snapshot;
                })
                .orElse(Map.of("deleted", true));
    }

    private Map<String, Object> snapshotCoupon(UUID id) {
        return couponRepository.findById(id)
                .map(coupon -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("code", coupon.getCode());
                    snapshot.put("type", coupon.getType());
                    snapshot.put("discountValue", coupon.getDiscountValue());
                    snapshot.put("maxDiscount", coupon.getMaxDiscount());
                    snapshot.put("minOrderAmount", coupon.getMinOrderAmount());
                    snapshot.put("startAt", coupon.getStartAt());
                    snapshot.put("endAt", coupon.getEndAt());
                    snapshot.put("usageLimit", coupon.getUsageLimit());
                    snapshot.put("usedCount", coupon.getUsedCount());
                    snapshot.put("active", coupon.isActive());
                    return snapshot;
                })
                .orElse(Map.of("deleted", true));
    }

    private Map<String, Object> snapshotVariant(UUID id) {
        if (id == null) {
            return Map.of();
        }
        return productVariantRepository.findById(id)
                .map(variant -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("productVariantId", variant.getId());
                    snapshot.put("variantName", variant.getName());
                    snapshot.put("productName", variant.getProduct() != null ? variant.getProduct().getProductName() : null);
                    snapshot.put("stockQuantity", variant.getStockQuantity());
                    snapshot.put("deleted", variant.isDeleted());
                    return snapshot;
                })
                .orElse(Map.of());
    }

    private void appendDiffMetadata(
            Map<String, Object> metadata,
            Map<String, Object> beforeSnapshot,
            Map<String, Object> afterSnapshot,
            ActivityAction action
    ) {
        if (!isDiffAction(action) || beforeSnapshot == null || beforeSnapshot.isEmpty()) {
            return;
        }
        Map<String, Object> effectiveAfter = afterSnapshot == null || afterSnapshot.isEmpty()
                ? Map.of("deleted", true)
                : afterSnapshot;
        List<String> changedFields = changedFields(beforeSnapshot, effectiveAfter);
        if (changedFields.isEmpty()) {
            return;
        }
        metadata.put("before", beforeSnapshot);
        metadata.put("after", effectiveAfter);
        metadata.put("changedFields", changedFields);
    }

    private boolean isDiffAction(ActivityAction action) {
        return action == ActivityAction.CHANGE_ROLE
                || action == ActivityAction.UPDATE_ACCOUNT
                || action == ActivityAction.LOCK_ACCOUNT
                || action == ActivityAction.UNLOCK_ACCOUNT
                || action == ActivityAction.UPDATE_ORDER_STATUS
                || action == ActivityAction.CANCEL_ORDER
                || action == ActivityAction.UPDATE_PRODUCT
                || action == ActivityAction.DELETE_PRODUCT
                || action == ActivityAction.UPDATE_PRODUCT_GROUP
                || action == ActivityAction.DELETE_PRODUCT_GROUP
                || action == ActivityAction.UPDATE_COUPON
                || action == ActivityAction.DELETE_COUPON
                || action == ActivityAction.UPDATE_STOCK;
    }

    private List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        List<String> fields = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            if (!Objects.equals(String.valueOf(before.get(key)), String.valueOf(after.get(key)))) {
                fields.add(key);
            }
        }
        return fields;
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
        if (cleanUri.contains("/product-groups")) return "PRODUCT_GROUP";
        if (cleanUri.contains("/products")) return "PRODUCT";
        if (cleanUri.contains("/categories")) return "CATEGORY";
        if (cleanUri.contains("/orders")) return "ORDER";
        if (cleanUri.contains("/customers")) return "CUSTOMER";
        if (cleanUri.contains("/accounts")) return "ACCOUNT";
        if (cleanUri.contains("/coupons")) return "COUPON";
        if (cleanUri.contains("/vouchers")) return "VOUCHER";
        if (cleanUri.contains("/auth")) return "AUTH";
        if (cleanUri.contains("/payments")) return "PAYMENT";
        if (cleanUri.contains("/ai")) return "AI_AGENT";
        if (cleanUri.contains("/logs")) return "LOG";
        return "SYSTEM";
    }

    private String extractTargetLabel(Object result, String targetId, String targetType) {
        if (result == null) {
            return targetId.isBlank() ? targetType : targetId;
        }
        Object body = result;
        if (result instanceof ResponseEntity<?> responseEntity) {
            body = responseEntity.getBody();
        }
        if (body instanceof ApiResponse<?> apiResponse) {
            body = apiResponse.getData();
        }

        if (body == null) {
            return targetId.isBlank() ? targetType : targetId;
        }

        if (body instanceof AuthResponse authResponse) {
            String label = firstNonBlank(authResponse.getEmail(), authResponse.getFullName(), authResponse.getAccountId());
            if (!label.isBlank()) return label;
        } else if (body instanceof CustomerDetailResponse customerDetail) {
            String label = firstNonBlank(customerDetail.getEmail(), customerDetail.getFullName());
            if (!label.isBlank()) return label;
        } else if (body instanceof ProductGroupResponse productGroup) {
            String label = productGroup.getGroupName();
            if (label != null && !label.isBlank()) return label;
        } else if (body instanceof AiAgentResponse aiAgent) {
            String label = aiAgent.getName();
            if (label != null && !label.isBlank()) return label;
        } else if (body instanceof AiDatasetResponse aiDataset) {
            String label = aiDataset.getName();
            if (label != null && !label.isBlank()) return label;
        } else if (body instanceof AiDocumentResponse aiDocument) {
            String label = aiDocument.getFileName();
            if (label != null && !label.isBlank()) return label;
        }

        for (String getter : List.of("getEmail", "getName", "getGroupName", "getFileName", "getCode", "getProductName")) {
            try {
                Method method = body.getClass().getMethod(getter);
                Object value = method.invoke(body);
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (Exception ignored) {
            }
        }

        return targetId.isBlank() ? targetType : targetId;
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

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
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

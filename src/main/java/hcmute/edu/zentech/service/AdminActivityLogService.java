package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ActivityTimelineAiLogItem;
import hcmute.edu.zentech.dto.request.ActivityTimelineAiSummaryRequest;
import hcmute.edu.zentech.dto.request.ActivityTimelineSummaryRequest;
import hcmute.edu.zentech.dto.response.ActivityLogResponseDto;
import hcmute.edu.zentech.dto.response.ActivityTimelineSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivityLog;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.ActivityLogRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final AccountUserRepository accountUserRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final R2StorageService r2StorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AdminAiRealtimeLogPublisher realtimeLogPublisher;

    private static final String ACTIVITY_LOG_TOPIC = "/topic/admin.activity-logs";
    private static final long RECORDING_RETENTION_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_RECORDING_SESSIONS_PER_USER = 50;
    public static final int MAX_RECORDING_EVENTS_PER_CHUNK = 2_000;
    private static final int MAX_RECORDING_EVENTS_PER_SESSION = 50_000;
    private static final int MAX_RECORDING_BYTES_PER_SESSION = 10 * 1024 * 1024;
    private static final int RECORDING_LOCK_STRIPES = 64;

    private final Object[] recordingLocks = createRecordingLocks();

    public void log(ActivityAction action, String targetType, String targetId, String description) {
        UUID currentUserId = SecurityContextUtils.getCurrentUserId();
        log(currentUserId, action, targetType, targetId, description);
    }

    public void log(UUID userId, ActivityAction action, String targetType, String targetId, String description) {
        log(userId, ActivityArea.SYSTEM, "SYSTEM", action, ActivitySeverity.INFO, targetType, targetId, null, description, null);
    }

    public void log(
            UUID userId,
            ActivityArea area,
            String module,
            ActivityAction action,
            ActivitySeverity severity,
            String targetType,
            String targetId,
            String targetLabel,
            String summary,
            String metadata
    ) {
        try {
            if (userId == null) {
                userId = SecurityContextUtils.getCurrentUserId();
            }
            AccountUser user = null;
            if (userId != null) {
                user = accountUserRepository.findById(userId).orElse(null);
            }

            ActivityArea effectiveArea = resolveArea(area, module, user);
            String effectiveTargetLabel = resolveTargetLabel(targetLabel, targetType, user);
            String effectiveSummary = resolveSummary(summary, action, module, user);

            HttpServletRequest request = getCurrentRequest();
            String ipAddress = getClientIp(request);
            String userAgent = getUserAgent(request);
            String traceId = trim(MDC.get("traceId"), 64);

            String safeSummary = trim(sanitizeText(effectiveSummary), 1000);
            String safeDescription = trim(sanitizeText(effectiveSummary), 1000);
            String safeMetadata = sanitizeText(metadata);
            if (safeMetadata != null && safeMetadata.length() > 4000) {
                safeMetadata = safeMetadata.substring(0, 3995) + "...";
            }

            if (safeDescription == null || safeDescription.isBlank()) {
                safeDescription = buildFallbackSummary(action, targetType, targetLabel, targetId);
            }

            if (safeDescription != null && safeDescription.length() > 1000) {
                safeDescription = safeDescription.substring(0, 995) + "...";
            }

            String safeUserAgent = userAgent;
            if (safeUserAgent != null && safeUserAgent.length() > 255) {
                safeUserAgent = safeUserAgent.substring(0, 250) + "...";
            }

            ActivityLog activityLog = ActivityLog.builder()
                    .user(user)
                    .area(effectiveArea)
                    .module(trim(module, 80))
                    .action(action)
                    .severity(severity != null ? severity : ActivitySeverity.INFO)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetLabel(trim(effectiveTargetLabel, 255))
                    .summary(safeSummary != null && !safeSummary.isBlank() ? safeSummary : safeDescription)
                    .description(safeDescription)
                    .metadata(safeMetadata)
                    .ipAddress(ipAddress)
                    .userAgent(safeUserAgent)
                    .traceId(traceId)
                    .createdAt(Instant.now())
                    .build();

            ActivityLog savedLog = activityLogRepository.save(activityLog);
            publishRealtimeActivityLog(savedLog);
            log.debug("Activity logged: {} in {} by user {} from IP {}", action, effectiveArea, user != null ? user.getEmail() : "anonymous", ipAddress);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage(), e);
        }
    }

    public PageResponse<ActivityLogResponseDto> getActivityLogs(
            int page,
            int size,
            String search,
            ActivityArea area,
            ActivitySeverity severity,
            String module,
            ActivityAction action,
            Instant from,
            Instant to
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String preprocessedSearch = preprocessSearchTerm(search);
        Page<ActivityLog> logPage = activityLogRepository.searchLogs(
                preprocessedSearch,
                area,
                severity,
                module,
                action,
                from,
                to,
                pageable
        );

        List<UUID> userIds = logPage.getContent().stream()
                .map(ActivityLog::getUser)
                .filter(java.util.Objects::nonNull)
                .map(AccountUser::getId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, Customer> customerMap = new java.util.HashMap<>();
        Map<UUID, Employee> employeeMap = new java.util.HashMap<>();

        if (!userIds.isEmpty()) {
            customerRepository.findByUserInfo_IdIn(userIds).forEach(c -> {
                if (c.getUserInfo() != null) {
                    customerMap.put(c.getUserInfo().getId(), c);
                }
            });
            employeeRepository.findByUserInfo_IdIn(userIds).forEach(e -> {
                if (e.getUserInfo() != null) {
                    employeeMap.put(e.getUserInfo().getId(), e);
                }
            });
        }

        final Map<UUID, Customer> finalCustMap = customerMap;
        final Map<UUID, Employee> finalEmpMap = employeeMap;

        List<ActivityLogResponseDto> dtoList = logPage.getContent().stream()
                .map(log -> mapToDto(log, finalCustMap, finalEmpMap))
                .collect(Collectors.toList());

        return PageResponse.from(logPage, dtoList);
    }

    public PageResponse<ActivityLogResponseDto> getActivityLogs(int page, int size, String search) {
        return getActivityLogs(page, size, search, null, null, null, null, null, null);
    }

    public PageResponse<ActivityLogResponseDto> getActivityTimeline(
            UUID userId,
            String email,
            Instant from,
            Instant to,
            int page,
            int size,
            ActivitySeverity severity,
            String module,
            ActivityAction action
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ActivityLog> logPage = activityLogRepository.searchTimeline(
                userId,
                email,
                from,
                to,
                severity,
                module,
                action,
                pageable
        );

        List<UUID> userIds = logPage.getContent().stream()
                .map(ActivityLog::getUser)
                .filter(java.util.Objects::nonNull)
                .map(AccountUser::getId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, Customer> customerMap = new java.util.HashMap<>();
        Map<UUID, Employee> employeeMap = new java.util.HashMap<>();

        if (!userIds.isEmpty()) {
            customerRepository.findByUserInfo_IdIn(userIds).forEach(c -> {
                if (c.getUserInfo() != null) {
                    customerMap.put(c.getUserInfo().getId(), c);
                }
            });
            employeeRepository.findByUserInfo_IdIn(userIds).forEach(e -> {
                if (e.getUserInfo() != null) {
                    employeeMap.put(e.getUserInfo().getId(), e);
                }
            });
        }

        Map<UUID, Customer> finalCustomerMap = customerMap;
        Map<UUID, Employee> finalEmployeeMap = employeeMap;
        List<ActivityLogResponseDto> dtoList = logPage.getContent().stream()
                .map(log -> mapToDto(log, finalCustomerMap, finalEmployeeMap))
                .collect(Collectors.toList());

        return PageResponse.from(logPage, dtoList);
    }

    public ActivityTimelineSummaryResponse summarizeTimeline(ActivityTimelineSummaryRequest request) {
        int requestedSize = request != null && request.getSize() != null ? request.getSize() : 50;
        int size = Math.max(1, Math.min(requestedSize, 100));
        UUID userId = request != null ? request.getUserId() : null;
        String email = request != null ? request.getEmail() : "";
        Instant from = request != null ? request.getFrom() : null;
        Instant to = request != null ? request.getTo() : null;
        ActivitySeverity severity = request != null ? request.getSeverity() : null;
        String module = request != null ? request.getModule() : null;
        ActivityAction action = request != null ? request.getAction() : null;

        Page<ActivityLog> logPage = activityLogRepository.searchTimeline(
                userId,
                email,
                from,
                to,
                severity,
                module,
                action,
                PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "createdAt"))
        );
        List<ActivityLog> logs = logPage.getContent();
        List<String> fallbackLines = buildTimelineFallbackSummary(logs, email);
        if (logs.isEmpty()) {
            return ActivityTimelineSummaryResponse.builder()
                    .lines(fallbackLines)
                    .fallback(true)
                    .build();
        }

        Optional<ActivityTimelineSummaryResponse> aiSummary = requestActivityTimelineSummaryFromAi(
                buildActivityTimelineAiRequest(request, logs, size)
        );

        if (aiSummary.isPresent() && aiSummary.get().getLines() != null && !aiSummary.get().getLines().isEmpty()) {
            return ActivityTimelineSummaryResponse.builder()
                    .lines(aiSummary.get().getLines())
                    .fallback(false)
                    .build();
        }

        return ActivityTimelineSummaryResponse.builder()
                .lines(fallbackLines)
                .fallback(true)
                .build();
    }

    private ActivityTimelineAiSummaryRequest buildActivityTimelineAiRequest(
            ActivityTimelineSummaryRequest request,
            List<ActivityLog> logs,
            int size
    ) {
        return ActivityTimelineAiSummaryRequest.builder()
                .userId(request != null && request.getUserId() != null ? request.getUserId().toString() : null)
                .email(request != null ? maskEmailForAi(request.getEmail()) : null)
                .from(request != null ? request.getFrom() : null)
                .to(request != null ? request.getTo() : null)
                .severity(request != null && request.getSeverity() != null ? request.getSeverity().name() : null)
                .module(request != null ? request.getModule() : null)
                .action(request != null && request.getAction() != null ? request.getAction().name() : null)
                .size(size)
                .logs(logs.stream().map(this::toActivityTimelineAiLogItem).toList())
                .build();
    }

    private ActivityTimelineAiLogItem toActivityTimelineAiLogItem(ActivityLog logItem) {
        AccountUser user = logItem.getUser();
        return ActivityTimelineAiLogItem.builder()
                .timestamp(logItem.getCreatedAt() != null ? logItem.getCreatedAt().toString() : null)
                .operatorEmail(user != null ? maskEmailForAi(user.getEmail()) : null)
                .operatorRole(user != null && user.getRole() != null ? user.getRole().name() : null)
                .area(logItem.getArea() != null ? logItem.getArea().name() : null)
                .module(logItem.getModule())
                .action(logItem.getAction() != null ? logItem.getAction().name() : null)
                .actionLabel(toActionLabel(logItem.getAction()))
                .severity(logItem.getSeverity() != null ? logItem.getSeverity().name() : null)
                .targetType(logItem.getTargetType())
                .targetId(redactForAi(logItem.getTargetId()))
                .targetLabel(redactForAi(logItem.getTargetLabel()))
                .summary(redactForAi(firstNonBlank(logItem.getSummary(), logItem.getDescription(), "N/A")))
                .metadata(redactForAi(logItem.getMetadata()))
                .ipAddress(classifyIpForAi(logItem.getIpAddress()))
                .userAgent(summarizeUserAgentForAi(logItem.getUserAgent()))
                .traceId(logItem.getTraceId() != null && !logItem.getTraceId().isBlank() ? "present" : null)
                .build();
    }

    private Optional<ActivityTimelineSummaryResponse> requestActivityTimelineSummaryFromAi(
            ActivityTimelineAiSummaryRequest requestPayload
    ) {
        String url = normalizeAiBaseUrl(aiBaseUrl) + "/admin/activity-timeline/summary";
        try {
            realtimeLogPublisher.publishAiInfo("Starting LLM call for activity timeline summary: log_count=" + requestPayload.getLogs().size());
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String traceId = MDC.get("traceId");
            if (traceId != null && !traceId.isBlank()) {
                headers.set("X-Trace-Id", traceId.trim());
            }

            org.springframework.http.HttpEntity<ActivityTimelineAiSummaryRequest> entity =
                    new org.springframework.http.HttpEntity<>(requestPayload, headers);
            org.springframework.http.ResponseEntity<ActivityTimelineSummaryResponse> response = restTemplate.postForEntity(
                    url,
                    entity,
                    ActivityTimelineSummaryResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                realtimeLogPublisher.publishAiInfo("Activity timeline summary completed: log_count=" + requestPayload.getLogs().size());
                return Optional.of(response.getBody());
            }
        } catch (Exception ex) {
            realtimeLogPublisher.publishAiError("Activity timeline summary failed", ex);
            log.warn("Failed to summarize activity timeline using AI service: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    private String normalizeAiBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
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

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "unknown";
    }

    private ActivityLogResponseDto mapToDto(ActivityLog activityLog) {
        return mapToDto(activityLog, null, null);
    }

    private ActivityLogResponseDto mapToDto(
            ActivityLog activityLog,
            Map<UUID, Customer> customerMap,
            Map<UUID, Employee> employeeMap
    ) {
        String email = "anonymous@zentech.local";
        String fullName = "Anonymous";
        String avatar = null;
        Role operatorRole = null;

        if (activityLog.getUser() != null) {
            email = activityLog.getUser().getEmail();
            operatorRole = activityLog.getUser().getRole();
            UUID accountId = activityLog.getUser().getId();
            
            if (activityLog.getArea() == hcmute.edu.zentech.model.ActivityArea.CUSTOMER) {
                // If it is customer area, search in Customer first
                Customer cust = customerMap != null ? customerMap.get(accountId) : null;
                if (cust != null) {
                    fullName = cust.getFullName();
                    avatar = cust.getImageUrl();
                } else {
                    Employee emp = employeeMap != null ? employeeMap.get(accountId) : null;
                    if (emp != null) {
                        fullName = emp.getFullName();
                        avatar = emp.getImageUrl();
                    } else {
                        Optional<Customer> custOpt = customerRepository.findByUserInfo_Id(accountId);
                        if (custOpt.isPresent()) {
                            fullName = custOpt.get().getFullName();
                            avatar = custOpt.get().getImageUrl();
                        } else {
                            Optional<Employee> empOpt = employeeRepository.findByUserInfo_Id(accountId);
                            if (empOpt.isPresent()) {
                                fullName = empOpt.get().getFullName();
                                avatar = empOpt.get().getImageUrl();
                            }
                        }
                    }
                }
            } else {
                // Otherwise (MANAGEMENT, ADMIN, SYSTEM), search in Employee first
                Employee emp = employeeMap != null ? employeeMap.get(accountId) : null;
                if (emp != null) {
                    fullName = emp.getFullName();
                    avatar = emp.getImageUrl();
                } else {
                    Customer cust = customerMap != null ? customerMap.get(accountId) : null;
                    if (cust != null) {
                        fullName = cust.getFullName();
                        avatar = cust.getImageUrl();
                    } else {
                        Optional<Employee> empOpt = employeeRepository.findByUserInfo_Id(accountId);
                        if (empOpt.isPresent()) {
                            fullName = empOpt.get().getFullName();
                            avatar = empOpt.get().getImageUrl();
                        } else {
                            Optional<Customer> custOpt = customerRepository.findByUserInfo_Id(accountId);
                            if (custOpt.isPresent()) {
                                fullName = custOpt.get().getFullName();
                                avatar = custOpt.get().getImageUrl();
                            }
                        }
                    }
                }
            }

            if (avatar != null && !avatar.trim().isEmpty() && !avatar.startsWith("http")) {
                avatar = r2StorageService.getPresignedGetUrl(avatar);
            }
        }

        StringBuilder targetBuilder = new StringBuilder();
        if (activityLog.getTargetType() != null && !activityLog.getTargetType().isEmpty()) {
            targetBuilder.append("[").append(activityLog.getTargetType()).append("]");
        }
        if (activityLog.getTargetId() != null && !activityLog.getTargetId().isEmpty()) {
            targetBuilder.append(" ID: ").append(activityLog.getTargetId());
        }
        if (activityLog.getDescription() != null && !activityLog.getDescription().isEmpty()) {
            if (targetBuilder.length() > 0) {
                targetBuilder.append(" - ");
            }
            targetBuilder.append(activityLog.getDescription());
        }
        String target = activityLog.getTargetLabel();
        if (target == null || target.isBlank()) {
            target = targetBuilder.length() > 0 ? targetBuilder.toString() : "N/A";
        }

        return ActivityLogResponseDto.builder()
                .id(activityLog.getId())
                .operatorEmail(email)
                .operatorFullName(fullName)
                .operatorAvatar(avatar)
                .operatorRole(operatorRole)
                .area(activityLog.getArea())
                .module(activityLog.getModule())
                .action(activityLog.getAction())
                .actionLabel(toActionLabel(activityLog.getAction()))
                .severity(activityLog.getSeverity())
                .targetType(activityLog.getTargetType())
                .targetId(activityLog.getTargetId())
                .targetLabel(activityLog.getTargetLabel())
                .target(target)
                .summary(firstNonBlank(activityLog.getSummary(), activityLog.getDescription(), target))
                .metadata(activityLog.getMetadata())
                .ipAddress(activityLog.getIpAddress())
                .userAgent(activityLog.getUserAgent())
                .traceId(activityLog.getTraceId())
                .timestamp(activityLog.getCreatedAt())
                .build();
    }

    public List<String> getDistinctModules() {
        return activityLogRepository.findDistinctModules();
    }

    public List<hcmute.edu.zentech.model.ActivityAction> getDistinctActions() {
        return activityLogRepository.findDistinctActions();
    }

    private List<String> buildTimelineFallbackSummary(List<ActivityLog> logs, String email) {
        if (logs.isEmpty()) {
            return List.of("Chua co du lieu timeline de tom tat cho user nay.");
        }
        long riskCount = logs.stream()
                .filter(log -> log.getSeverity() == ActivitySeverity.IMPORTANT
                        || log.getSeverity() == ActivitySeverity.SECURITY
                        || log.getSeverity() == ActivitySeverity.CRITICAL)
                .count();
        long diffCount = logs.stream().filter(this::hasDiffMetadata).count();
        long uniqueIpCount = logs.stream()
                .map(ActivityLog::getIpAddress)
                .filter(ip -> ip != null && !ip.isBlank())
                .distinct()
                .count();
        String modules = logs.stream()
                .map(ActivityLog::getModule)
                .filter(module -> module != null && !module.isBlank())
                .collect(Collectors.groupingBy(module -> module, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
        ActivityLog first = logs.get(0);
        ActivityLog last = logs.get(logs.size() - 1);
        String userLabel = firstNonBlank(email, first.getUser() != null ? first.getUser().getEmail() : null, "user nay");
        return List.of(
                userLabel + " co " + logs.size() + " hanh dong trong timeline, tu " + first.getCreatedAt() + " den " + last.getCreatedAt() + ".",
                "Module noi bat: " + firstNonBlank(modules, "chua ro") + ".",
                "Co " + riskCount + " hanh dong can chu y, " + uniqueIpCount + " IP khac nhau va " + diffCount + " buoc co du lieu so sanh thay doi.",
                diffCount > 0 ? "Nen kiem tra cac buoc co before/after de doi chieu thay doi quan trong." : "Chua co before/after trong cac buoc timeline hien tai."
        );
    }

    private boolean hasDiffMetadata(ActivityLog log) {
        String metadata = log.getMetadata();
        return metadata != null
                && metadata.contains("\"before\"")
                && metadata.contains("\"after\"")
                && metadata.contains("\"changedFields\"");
    }

    private void publishRealtimeActivityLog(ActivityLog activityLog) {
        try {
            messagingTemplate.convertAndSend(ACTIVITY_LOG_TOPIC, mapToDto(activityLog));
        } catch (Exception e) {
            log.warn("Failed to publish realtime activity log {}: {}", activityLog.getId(), e.getMessage());
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
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

    private String maskEmailForAi(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmedEmail = email.trim();
        int atIndex = trimmedEmail.indexOf('@');
        if (atIndex <= 0 || atIndex >= trimmedEmail.length() - 1) {
            return "[EMAIL]";
        }
        return trimmedEmail.charAt(0) + "***@" + trimmedEmail.substring(atIndex + 1).toLowerCase();
    }

    private String classifyIpForAi(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        String ip = ipAddress.trim();
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return "localhost";
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return "private-network";
        }
        return "public-ip";
    }

    private String summarizeUserAgentForAi(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || "unknown".equalsIgnoreCase(userAgent)) {
            return null;
        }
        String ua = userAgent.toLowerCase();
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("chrome/") ? "Chrome"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("safari/") ? "Safari"
                : "Other browser";
        String os = ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("linux") ? "Linux"
                : "Other OS";
        String device = ua.contains("mobile") || ua.contains("iphone") || ua.contains("android") ? "Mobile" : "Desktop";
        return browser + " / " + os + " / " + device;
    }

    private String redactForAi(String value) {
        if (value == null) {
            return null;
        }
        String redacted = sanitizeText(value);
        redacted = redacted.replaceAll(
                "(?i)(\\\"(?:address|shippingAddress|billingAddress|phone|phoneNumber|customerName|fullName|email)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")",
                "$1[REDACTED]$2"
        );
        redacted = redacted.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[EMAIL]");
        redacted = redacted.replaceAll("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b", "[ID]");
        redacted = redacted.replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "[IP]");
        redacted = redacted.replaceAll("(?<!\\d)(?:\\+?84|0)\\d{8,10}(?!\\d)", "[PHONE]");
        return trim(redacted, 1500);
    }
    private ActivityArea resolveArea(ActivityArea area, String module, AccountUser user) {
        if (area != null && area != ActivityArea.SYSTEM) {
            return area;
        }
        if (user == null || user.getRole() == null || module == null || !module.equalsIgnoreCase("AUTH")) {
            return area != null ? area : ActivityArea.SYSTEM;
        }
        Role role = user.getRole();
        if (role == Role.ADMIN) {
            return ActivityArea.ADMIN;
        }
        if (role == Role.OWNER || role == Role.MANAGER || role == Role.EMPLOYEE) {
            return ActivityArea.MANAGEMENT;
        }
        if (role == Role.CUSTOMER) {
            return ActivityArea.CUSTOMER;
        }
        return ActivityArea.SYSTEM;
    }

    private String resolveTargetLabel(String targetLabel, String targetType, AccountUser user) {
        if (targetLabel != null && !targetLabel.isBlank() && !targetLabel.equalsIgnoreCase(targetType)) {
            return targetLabel;
        }
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return targetLabel;
    }

    private String resolveSummary(String summary, ActivityAction action, String module, AccountUser user) {
        if ((summary == null || summary.isBlank()) && user != null) {
            return action.name() + " - " + user.getEmail();
        }
        if (user == null || module == null || !module.equalsIgnoreCase("AUTH")) {
            return normalizeSummary(summary);
        }
        String authSummary = normalizeSummary(summary);
        return switch (action) {
            case LOGIN -> buildUserAuthSummary(user.getEmail(), authSummary, "đăng nhập");
            case LOGIN_FAILED -> buildUserAuthSummary(user.getEmail(), authSummary, "đăng nhập thất bại");
            case LOGOUT -> buildUserAuthSummary(user.getEmail(), authSummary, "đăng xuất");
            case PASSWORD_CHANGED -> buildUserAuthSummary(user.getEmail(), authSummary, "đổi mật khẩu");
            default -> authSummary;
        };
    }

    private String buildUserAuthSummary(String email, String summary, String fallbackAction) {
        String actionSummary = firstNonBlank(summary, fallbackAction);
        if (email == null || email.isBlank()) {
            return formatSentence(actionSummary);
        }
        if (actionSummary.toLowerCase().startsWith(email.toLowerCase())) {
            return formatSentence(actionSummary);
        }
        return email + " " + decapitalizeFirst(actionSummary);
    }

    private String decapitalizeFirst(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 1).toLowerCase() + trimmed.substring(1);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "N/A";
    }

    private String buildFallbackSummary(ActivityAction action, String targetType, String targetLabel, String targetId) {
        String target = firstNonBlank(targetLabel, targetId, targetType, "hệ thống");
        return formatSentence(toActionLabel(action) + " trên " + target);
    }

    private String toActionLabel(ActivityAction action) {
        if (action == null) {
            return "Không xác định";
        }
        return switch (action) {
            case LOGIN -> "Đăng nhập";
            case LOGIN_FAILED -> "Đăng nhập thất bại";
            case LOGOUT -> "Đăng xuất";
            case PASSWORD_CHANGED -> "Đổi mật khẩu";
            case PASSWORD_RESET_REQUESTED -> "Yêu cầu đặt lại mật khẩu";
            case PASSWORD_RESET_COMPLETED -> "Đặt lại mật khẩu";
            case ACCESS_DENIED -> "Từ chối truy cập";
            case CREATE_ACCOUNT -> "Tạo tài khoản";
            case UPDATE_ACCOUNT -> "Cập nhật tài khoản";
            case DELETE_ACCOUNT -> "Xóa tài khoản";
            case LOCK_ACCOUNT -> "Khóa tài khoản";
            case UNLOCK_ACCOUNT -> "Mở khóa tài khoản";
            case CHANGE_ROLE -> "Đổi vai trò";
            case CHANGE_PERMISSION -> "Đổi phân quyền";
            case CHECKOUT_STARTED -> "Bắt đầu đặt hàng";
            case CHECKOUT_COMPLETED -> "Đặt hàng thành công";
            case CHECKOUT_FAILED -> "Đặt hàng thất bại";
            case PAYMENT_STARTED -> "Bắt đầu thanh toán";
            case PAYMENT_COMPLETED -> "Thanh toán thành công";
            case PAYMENT_FAILED -> "Thanh toán thất bại";
            case ORDER_CANCELLED_BY_CUSTOMER -> "Khách hủy đơn hàng";
            case REVIEW_CREATED -> "Tạo đánh giá";
            case REVIEW_UPDATED -> "Cập nhật đánh giá";
            case REVIEW_DELETED -> "Xóa đánh giá";
            case CREATE_PRODUCT -> "Tạo sản phẩm";
            case UPDATE_PRODUCT -> "Cập nhật sản phẩm";
            case DELETE_PRODUCT -> "Xóa sản phẩm";
            case UPDATE_PRODUCT_STATUS -> "Cập nhật trạng thái sản phẩm";
            case UPDATE_PRICE -> "Cập nhật giá";
            case UPDATE_STOCK -> "Cập nhật tồn kho";
            case IMPORT_STOCK -> "Nhập kho";
            case EXPORT_STOCK -> "Xuất kho";
            case CREATE_COUPON -> "Tạo mã giảm giá";
            case UPDATE_COUPON -> "Cập nhật mã giảm giá";
            case DELETE_COUPON -> "Xóa mã giảm giá";
            case ISSUE_VOUCHER -> "Phát voucher";
            case REVOKE_VOUCHER -> "Thu hồi voucher";
            case UPDATE_ORDER_STATUS -> "Cập nhật đơn hàng";
            case CANCEL_ORDER -> "Hủy đơn hàng";
            case ASSIGN_ORDER -> "Phân công đơn hàng";
            case CREATE_EMPLOYEE -> "Tạo nhân viên";
            case UPDATE_EMPLOYEE -> "Cập nhật nhân viên";
            case DELETE_EMPLOYEE -> "Xóa nhân viên";
            case UPDATE_SHIFT -> "Cập nhật ca làm";
            case CHECK_IN -> "Chấm công vào";
            case CHECK_OUT -> "Chấm công ra";
            case CREATE_TICKET -> "Tạo ticket";
            case UPDATE_TICKET_STATUS -> "Cập nhật trạng thái ticket";
            case ASSIGN_TICKET -> "Gán ticket";
            case REPLY_TICKET -> "Phản hồi ticket";
            case CLOSE_TICKET -> "Đóng ticket";
            case STAFF_JOIN_CHAT -> "Nhân viên vào chat";
            case STAFF_LEAVE_CHAT -> "Nhân viên rời chat";
            case VIEW_LOG_DETAIL -> "Xem chi tiết log";
            case EXPORT_LOG -> "Xuất log";
            case ARCHIVE_LOG -> "Lưu trữ log";
            case CLEAR_LOG -> "Xóa log hiển thị";
            case CREATE_INCIDENT -> "Tạo sự cố";
            case UPDATE_INCIDENT -> "Cập nhật sự cố";
            case RESOLVE_INCIDENT -> "Xử lý sự cố";
            case CREATE_AI_AGENT -> "Tạo AI agent";
            case UPDATE_AI_AGENT -> "Cập nhật AI agent";
            case DELETE_AI_AGENT -> "Xóa AI agent";
            case CHANGE_AI_AGENT_ROLE -> "Thay đổi vai trò AI agent";
            case CREATE_AI_DATASET -> "Tạo bộ dữ liệu AI";
            case UPDATE_AI_DATASET -> "Cập nhật bộ dữ liệu AI";
            case DELETE_AI_DATASET -> "Xóa bộ dữ liệu AI";
            case UPLOAD_AI_DOCUMENT -> "Tải lên tài liệu AI";
            case DELETE_AI_DOCUMENT -> "Xóa tài liệu AI";
            case UPDATE_SYSTEM_SETTING -> "Cập nhật cấu hình hệ thống";
            case REGISTER_FACE -> "Đăng ký khuôn mặt";
            case DELETE_FACE -> "Xóa khuôn mặt";
            case FACE_VERIFICATION_SUCCESS -> "Xác thực khuôn mặt thành công";
            case FACE_VERIFICATION_FAILED -> "Xác thực khuôn mặt thất bại";
            default -> action.name().replace('_', ' ');

        };
    }

    private String normalizeSummary(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replaceAll("(?i)cap nhat trang thai don hang", "cập nhật trạng thái đơn hàng")
                .replaceAll("(?i)dieu chinh ton kho", "điều chỉnh tồn kho")
                .replaceAll("(?i)dang nhap Google", "đăng nhập Google")
                .replaceAll("(?i)dang nhap he thong", "đăng nhập hệ thống")
                .replaceAll("(?i)dang xuat he thong", "đăng xuất hệ thống")
                .replaceAll("(?i)doi mat khau", "đổi mật khẩu")
                .replaceAll("(?i)khach hang", "khách hàng")
                .replaceAll("(?i)tai khoan", "tài khoản")
                .replaceAll("(?i)trang thai", "trạng thái")
                .replaceAll("(?i)don hang", "đơn hàng")
                .replaceAll("(?i)ton kho", "tồn kho")
                .replaceAll("(?i)san pham", "sản phẩm")
                .replaceAll("(?i)ma giam gia", "mã giảm giá")
                .replaceAll("(?i)noi bo", "nội bộ")
                .replaceAll("(?i)chi tiet", "chi tiết")
                .replaceAll("(?i)danh sach", "danh sách")
                .replaceAll("(?i)dang hien thi", "đang hiển thị")
                .replaceAll("(?i)thay doi", "thay đổi")
                .replaceAll("(?i)vai tro", "vai trò")
                .replaceAll("(?i)xoa", "xóa")
                .replaceAll("(?i)tao", "tạo")
                .replaceAll("(?i)huy", "hủy")
                .replaceAll("(?i)phat voucher", "phát voucher")
                .replaceAll("(?i)thu hoi voucher", "thu hồi voucher")
                .replaceAll("(?i)cap nhat", "cập nhật")
                .replaceAll("(?i)dang ky", "đăng ký")
                .replaceAll("(?i)bat/tat", "bật/tắt")
                .replaceAll("(?i)cua", "của");
        return formatSentence(normalized);
    }

    private String formatSentence(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
    }

    private String preprocessSearchTerm(String search) {
        if (search == null || search.isBlank()) {
            return search;
        }
        String clean = search.trim().toLowerCase();

        if (clean.contains("nội bộ") || clean.contains("noi bo")) {
            return "MANAGEMENT";
        }
        if (clean.contains("mua hàng") || clean.contains("mua hang")) {
            return "CUSTOMER";
        }
        if (clean.contains("hệ thống") || clean.contains("he thong")) {
            return "SYSTEM";
        }
        if (clean.equals("admin")) {
            return "ADMIN";
        }
        
        // Map Severity labels
        if (clean.contains("quan trọng") || clean.contains("quan trong")) {
            return "IMPORTANT";
        }
        if (clean.contains("nghiêm trọng") || clean.contains("nghiem trong")) {
            return "CRITICAL";
        }
        if (clean.contains("bảo mật") || clean.contains("bao mat")) {
            return "SECURITY";
        }
        if (clean.contains("thông tin") || clean.contains("thong tin")) {
            return "INFO";
        }

        return search;
    }

    public void saveRecording(String email, String sessionId, List<Object> newEvents) {
        validateRecordingPathPart(email, "email");
        if (newEvents == null || newEvents.isEmpty()) {
            return;
        }
        if (newEvents.size() > MAX_RECORDING_EVENTS_PER_CHUNK) {
            throw new IllegalArgumentException("Recording chunk exceeds the allowed event limit");
        }

        String activeSessionId = sessionId;
        if (activeSessionId == null || activeSessionId.isBlank()) {
            activeSessionId = Instant.now().toEpochMilli() + "_" + UUID.randomUUID();
        }
        validateRecordingPathPart(activeSessionId, "sessionId");

        String fileKey = "recordings/" + email + "/" + activeSessionId + ".json";
        Object lock = recordingLockFor(fileKey);
        synchronized (lock) {
            try {
                List<Object> existingEvents = new ArrayList<>();

                try {
                    byte[] bytes = r2StorageService.getObjectBytes(fileKey);
                    if (bytes != null && bytes.length > 0) {
                        existingEvents = objectMapper.readValue(bytes, new TypeReference<List<Object>>() {});
                    }
                } catch (Exception e) {
                    log.debug("No existing recording found on R2 for {} session {}, creating new.", email, activeSessionId);
                }

                int totalEvents = existingEvents.size() + newEvents.size();
                if (totalEvents > MAX_RECORDING_EVENTS_PER_SESSION) {
                    throw new IllegalArgumentException("Recording session exceeds the allowed event limit");
                }

                existingEvents.addAll(newEvents);
                byte[] jsonBytes = objectMapper.writeValueAsBytes(existingEvents);
                if (jsonBytes.length > MAX_RECORDING_BYTES_PER_SESSION) {
                    throw new IllegalArgumentException("Recording session exceeds the allowed storage size");
                }

                r2StorageService.uploadFile(fileKey, jsonBytes, "application/json");
                cleanupExpiredRecordingSessions(email, activeSessionId);
                log.debug("Saved {} new rrweb events to R2 for {} session {}. Total events: {}", newEvents.size(), email, activeSessionId, totalEvents);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to save recording on R2 for {} session {}: {}", email, activeSessionId, e.getMessage(), e);
            }
        }
    }

    public List<Object> getRecording(String email, String sessionId) {
        if (!isSafeRecordingPathPart(email)) {
            log.warn("Skipped reading rrweb recording because email contains unsafe path characters. email={}", email);
            return List.of();
        }
        if (sessionId != null && !sessionId.isBlank() && !isSafeRecordingPathPart(sessionId)) {
            log.warn("Skipped reading rrweb recording because sessionId contains unsafe path characters. email={}, sessionId={}", email, sessionId);
            return List.of();
        }

        try {
            String fileKey;
            if (sessionId == null || sessionId.isBlank()) {
                List<String> keys = r2StorageService.getAllObjectKeysInFolder("recordings/" + email + "/");
                if (keys.isEmpty()) {
                    String oldKey = "recordings/" + email + ".json";
                    try {
                        byte[] bytes = r2StorageService.getObjectBytes(oldKey);
                        return objectMapper.readValue(bytes, new TypeReference<List<Object>>() {});
                    } catch (Exception ex) {
                        return List.of();
                    }
                }
                Collections.sort(keys);
                fileKey = keys.get(keys.size() - 1);
            } else {
                fileKey = "recordings/" + email + "/" + sessionId + ".json";
            }

            byte[] bytes = r2StorageService.getObjectBytes(fileKey);
            if (bytes == null || bytes.length == 0) {
                return List.of();
            }
            return objectMapper.readValue(bytes, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to read recording from R2 for {} session {}: {}", email, sessionId, e.getMessage());
            return List.of();
        }
    }

    public List<Object> getRecordingByUserId(UUID userId, String sessionId) {
        return resolveRecordingEmail(userId)
                .map(email -> getRecording(email, sessionId))
                .orElseGet(List::of);
    }

    public List<Map<String, Object>> getRecordingSessions(String email) {
        if (!isSafeRecordingPathPart(email)) {
            log.warn("Skipped listing rrweb sessions because email contains unsafe path characters. email={}", email);
            return List.of();
        }
        try {
            cleanupExpiredRecordingSessions(email, null);
            List<String> keys = r2StorageService.getAllObjectKeysInFolder("recordings/" + email + "/");
            List<Map<String, Object>> sessions = new ArrayList<>();
            for (String key : keys) {
                String filename = key.substring(key.lastIndexOf("/") + 1);
                if (!filename.endsWith(".json")) {
                    continue;
                }
                String sessionId = filename.substring(0, filename.lastIndexOf("."));
                if (!isSafeRecordingPathPart(sessionId)) {
                    continue;
                }
                long timestamp = parseRecordingSessionTimestamp(sessionId);

                Map<String, Object> session = new java.util.HashMap<>();
                session.put("sessionId", sessionId);
                session.put("timestamp", timestamp > 0 ? timestamp : Instant.now().toEpochMilli());
                sessions.add(session);
            }

            sessions.sort((s1, s2) -> Long.compare((Long) s2.get("timestamp"), (Long) s1.get("timestamp")));
            return sessions;
        } catch (Exception e) {
            log.error("Failed to get recording sessions for {}: {}", email, e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getRecordingSessionsByUserId(UUID userId) {
        return resolveRecordingEmail(userId)
                .map(this::getRecordingSessions)
                .orElseGet(List::of);
    }

    private Optional<String> resolveRecordingEmail(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        return accountUserRepository.findById(userId)
                .map(AccountUser::getEmail)
                .filter(email -> email != null && !email.isBlank());
    }

    public void deleteRecording(String email, String sessionId) {
        if (!isSafeRecordingPathPart(email)) {
            log.warn("Skipped deleting rrweb recording because email contains unsafe path characters. email={}", email);
            return;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            if (!isSafeRecordingPathPart(sessionId)) {
                log.warn("Skipped deleting rrweb recording because sessionId contains unsafe path characters. email={}, sessionId={}", email, sessionId);
                return;
            }
            try {
                r2StorageService.deleteFile("recordings/" + email + "/" + sessionId + ".json");
                log.info("Deleted rrweb recording on R2 for {} session {}", email, sessionId);
            } catch (Exception e) {
                log.error("Failed to delete recording on R2 for {} session {}: {}", email, sessionId, e.getMessage());
            }
            return;
        }

        try {
            try {
                r2StorageService.deleteFile("recordings/" + email + ".json");
            } catch (Exception e) {
                // ignore old single-file path cleanup failures
            }

            List<String> keys = r2StorageService.getAllObjectKeysInFolder("recordings/" + email + "/");
            for (String key : keys) {
                r2StorageService.deleteFile(key);
            }
            log.info("Deleted all recordings on R2 for {}", email);
        } catch (Exception e) {
            log.error("Failed to delete recordings on R2 for {}: {}", email, e.getMessage());
        }
    }

    private void cleanupExpiredRecordingSessions(String email, String activeSessionId) {
        if (!isSafeRecordingPathPart(email)) {
            return;
        }
        try {
            List<String> keys = r2StorageService.getAllObjectKeysInFolder("recordings/" + email + "/");
            if (keys.isEmpty()) {
                return;
            }

            long cutoff = Instant.now().toEpochMilli() - RECORDING_RETENTION_MS;
            List<RecordingSessionObject> sessions = new ArrayList<>();
            for (String key : keys) {
                RecordingSessionObject session = toRecordingSessionObject(key);
                if (session != null) {
                    sessions.add(session);
                }
            }

            sessions.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
            for (int i = 0; i < sessions.size(); i++) {
                RecordingSessionObject session = sessions.get(i);
                boolean isActive = activeSessionId != null && activeSessionId.equals(session.sessionId());
                boolean expiredByAge = session.timestamp() > 0 && session.timestamp() < cutoff;
                boolean expiredByCount = i >= MAX_RECORDING_SESSIONS_PER_USER;
                if (!isActive && (expiredByAge || expiredByCount)) {
                    r2StorageService.deleteFile(session.key());
                    log.info("Deleted expired rrweb recording {} for {}", session.sessionId(), email);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup expired rrweb recordings for {}: {}", email, e.getMessage());
        }
    }

    private RecordingSessionObject toRecordingSessionObject(String key) {
        String filename = key.substring(key.lastIndexOf("/") + 1);
        if (!filename.endsWith(".json")) {
            return null;
        }
        String sessionId = filename.substring(0, filename.lastIndexOf("."));
        long timestamp = parseRecordingSessionTimestamp(sessionId);
        return new RecordingSessionObject(key, sessionId, timestamp);
    }

    private long parseRecordingSessionTimestamp(String sessionId) {
        if (sessionId == null || !sessionId.contains("_")) {
            return 0;
        }
        try {
            return Long.parseLong(sessionId.split("_")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record RecordingSessionObject(String key, String sessionId, long timestamp) {
    }

    private static Object[] createRecordingLocks() {
        Object[] locks = new Object[RECORDING_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private Object recordingLockFor(String fileKey) {
        return recordingLocks[Math.floorMod(fileKey.hashCode(), recordingLocks.length)];
    }

    private void validateRecordingPathPart(String value, String fieldName) {
        if (!isSafeRecordingPathPart(value)) {
            throw new IllegalArgumentException("Invalid recording " + fieldName);
        }
    }

    private boolean isSafeRecordingPathPart(String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("/")
                && !value.contains("\\")
                && !value.contains("..");
    }
}

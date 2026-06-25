package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.response.AdminStatisticsResponse;
import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.IncidentRepository;
import hcmute.edu.zentech.repository.TicketRepository;
import hcmute.edu.zentech.repository.projection.AccountSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminStatisticsService {
    private static final int LOG_LIMIT = 5000;
    private static final int MAX_LOG_REQUESTS_PER_LEVEL = 256;
    private static final int MAX_LOGS_PER_LEVEL = 100_000;
    private static final long MIN_LOG_WINDOW_MILLIS = 1_000;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern NUMBER_SEGMENT_PATTERN = Pattern.compile("(?<=/)\\d+(?=/|$)");

    private final AdminLogService adminLogService;
    private final IncidentRepository incidentRepository;
    private final TicketRepository ticketRepository;
    private final AccountUserRepository accountUserRepository;
    private final R2StorageService r2StorageService;
    private final ObjectMapper objectMapper;

    @Value("${app.dashboard.zone-id:Asia/Ho_Chi_Minh}")
    private String dashboardZoneId;

    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(String requestedPeriod, Instant customFrom, Instant customTo) {
        DateRange range = resolveRange(requestedPeriod, customFrom, customTo);
        LogFetchResult errorLogs = fetchLogs("ERROR", range.from(), range.to());
        LogFetchResult warningLogs = fetchLogs("WARN", range.from(), range.to());
        boolean logsAvailable = errorLogs.available() && warningLogs.available();
        boolean partialData = errorLogs.partial() || warningLogs.partial();

        List<ErrorEvent> events = new ArrayList<>();
        Set<String> seenTraceIds = new HashSet<>();
        if (errorLogs.available()) appendLogEvents(events, seenTraceIds, errorLogs.logs());
        if (warningLogs.available()) appendLogEvents(events, seenTraceIds, warningLogs.logs());

        List<Incident> incidents = incidentRepository.findByOccurredAtBetween(range.from(), range.to());
        for (Incident incident : incidents) {
            if (hasText(incident.getTraceId()) && seenTraceIds.contains(incident.getTraceId())) continue;
            events.add(toIncidentEvent(incident));
        }

        List<Ticket> cohort = ticketRepository.findByCreatedAtBetween(range.from(), range.to());
        long ticketsResolved = cohort.stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED)
                .count();

        return AdminStatisticsResponse.builder()
                .period(range.period())
                .from(range.from())
                .to(range.to())
                .generatedAt(Instant.now())
                .logsAvailable(logsAvailable)
                .partialData(partialData)
                .totalErrors(events.stream().filter(event -> "ERROR".equals(event.level())).count())
                .incidentsInPeriod(incidents.size())
                .ticketsCreated(cohort.size())
                .ticketsResolved(ticketsResolved)
                .ticketResolutionRate(cohort.isEmpty() ? 0 : roundOneDecimal(ticketsResolved * 100.0 / cohort.size()))
                .errorTrend(buildTrend(range, events))
                .topApis(buildTopApis(events))
                .topServices(buildTopServices(events))
                .topAffectedUsers(buildTopUsers(events))
                .build();
    }

    private LogFetchResult fetchLogs(String level, Instant from, Instant to) {
        Map<String, Map<String, Object>> uniqueLogs = new LinkedHashMap<>();
        ArrayDeque<TimeWindow> pending = new ArrayDeque<>();
        pending.push(new TimeWindow(from.toEpochMilli(), to.toEpochMilli()));
        int requestCount = 0;
        boolean partial = false;

        while (!pending.isEmpty()) {
            if (requestCount >= MAX_LOG_REQUESTS_PER_LEVEL || uniqueLogs.size() >= MAX_LOGS_PER_LEVEL) {
                partial = true;
                break;
            }

            TimeWindow window = pending.pop();
            List<Map<String, Object>> logs = adminLogService.getLogs(
                    level, "", "", LOG_LIMIT, window.fromMillis(), window.toMillis());
            requestCount++;

            if (!isLogSourceAvailable(logs)) {
                return new LogFetchResult(false, false, List.of());
            }

            if (logs.size() >= LOG_LIMIT && window.durationMillis() > MIN_LOG_WINDOW_MILLIS) {
                long midpoint = window.fromMillis() + window.durationMillis() / 2;
                pending.push(new TimeWindow(midpoint + 1, window.toMillis()));
                pending.push(new TimeWindow(window.fromMillis(), midpoint));
                continue;
            }

            appendUniqueLogs(uniqueLogs, logs);
            if (logs.size() >= LOG_LIMIT) partial = true;
        }

        return new LogFetchResult(true, partial, new ArrayList<>(uniqueLogs.values()));
    }

    private boolean isLogSourceAvailable(List<Map<String, Object>> logs) {
        return logs.stream().noneMatch(log -> "OFFLINE-LOG".equals(String.valueOf(log.get("id"))));
    }

    private void appendUniqueLogs(
            Map<String, Map<String, Object>> uniqueLogs,
            List<Map<String, Object>> logs
    ) {
        for (Map<String, Object> log : logs) {
            if (uniqueLogs.size() >= MAX_LOGS_PER_LEVEL) return;
            uniqueLogs.putIfAbsent(logIdentity(log), log);
        }
    }

    private String logIdentity(Map<String, Object> log) {
        String id = stringValue(log.get("id"));
        if (hasText(id)) return id;
        return stringValue(log.get("timestamp")) + "|" + stringValue(log.get("level"))
                + "|" + stringValue(log.get("message"));
    }

    private void appendLogEvents(
            List<ErrorEvent> events,
            Set<String> seenTraceIds,
            List<Map<String, Object>> logs
    ) {
        logs.stream().map(this::toLogEvent).filter(event -> event != null).forEach(event -> {
            events.add(event);
            if (hasText(event.traceId())) seenTraceIds.add(event.traceId());
        });
    }
    private ErrorEvent toLogEvent(Map<String, Object> log) {
        String level = stringValue(log.get("level")).toUpperCase(Locale.ROOT);
        if (!"WARN".equals(level) && !"ERROR".equals(level)) return null;
        JsonNode context = parseContext(stringValue(log.get("details")));
        return new ErrorEvent(
                level,
                toInstant(log.get("timestamp")),
                firstNonBlank(text(context, "method"), "HTTP").toUpperCase(Locale.ROOT),
                normalizeEndpoint(text(context, "apiPath")),
                integer(context, "statusCode"),
                normalizeService(stringValue(log.get("category"))),
                firstNonBlank(stringValue(log.get("traceId")), text(context, "traceId")),
                uuid(text(context, "userId")),
                lower(text(context, "userEmail")),
                role(text(context, "userRole"))
        );
    }

    private ErrorEvent toIncidentEvent(Incident incident) {
        return new ErrorEvent(
                "ERROR",
                firstNonNull(incident.getOccurredAt(), incident.getCreatedAt(), Instant.now()),
                firstNonBlank(incident.getHttpMethod(), "HTTP").toUpperCase(Locale.ROOT),
                normalizeEndpoint(incident.getApiPath()),
                incident.getStatusCode(),
                normalizeService(incident.getServiceName()),
                incident.getTraceId(),
                incident.getUser() == null ? null : incident.getUser().getId(),
                incident.getUser() == null ? null : lower(incident.getUser().getEmail()),
                incident.getUser() == null ? null : incident.getUser().getRole()
        );
    }

    private List<AdminStatisticsResponse.ErrorTrendPoint> buildTrend(DateRange range, List<ErrorEvent> events) {
        ZoneId zone = dashboardZone();
        Map<String, MutableTrend> buckets = new LinkedHashMap<>();
        ZonedDateTime cursor = range.hourly()
                ? range.from().atZone(zone).withMinute(0).withSecond(0).withNano(0)
                : range.from().atZone(zone).toLocalDate().atStartOfDay(zone);
        ZonedDateTime end = range.to().atZone(zone);
        while (!cursor.isAfter(end)) {
            String key = bucketKey(cursor.toInstant(), range.hourly(), zone);
            String label = cursor.format(DateTimeFormatter.ofPattern(range.hourly() ? "HH:mm" : "dd/MM"));
            buckets.put(key, new MutableTrend(key, label));
            cursor = range.hourly() ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        for (ErrorEvent event : events) {
            MutableTrend bucket = buckets.get(bucketKey(event.timestamp(), range.hourly(), zone));
            if (bucket == null) continue;
            bucket.total++;
            if ("ERROR".equals(event.level())) bucket.errors++;
            else bucket.warnings++;
        }
        return buckets.values().stream().map(MutableTrend::response).toList();
    }

    private List<AdminStatisticsResponse.ApiErrorItem> buildTopApis(List<ErrorEvent> events) {
        Map<String, ApiAggregate> grouped = new HashMap<>();
        events.stream().filter(event -> hasText(event.endpoint())).forEach(event -> {
            String key = event.method() + " " + event.endpoint();
            ApiAggregate aggregate = grouped.computeIfAbsent(key,
                    ignored -> new ApiAggregate(event.method(), event.endpoint()));
            aggregate.count++;
            aggregate.lastSeen = latest(aggregate.lastSeen, event.timestamp());
            if (event.statusCode() != null) aggregate.statusCounts.merge(event.statusCode(), 1L, Long::sum);
        });
        return grouped.values().stream()
                .sorted(Comparator.comparingLong((ApiAggregate value) -> value.count).reversed()
                        .thenComparing(value -> value.lastSeen, Comparator.reverseOrder()))
                .limit(5)
                .map(value -> AdminStatisticsResponse.ApiErrorItem.builder()
                        .method(value.method)
                        .endpoint(value.endpoint)
                        .statusCode(value.statusCounts.entrySet().stream()
                                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null))
                        .errorCount(value.count)
                        .lastSeen(value.lastSeen)
                        .build())
                .toList();
    }

    private List<AdminStatisticsResponse.ServiceErrorItem> buildTopServices(List<ErrorEvent> events) {
        Map<String, ServiceAggregate> grouped = new HashMap<>();
        for (ErrorEvent event : events) {
            ServiceAggregate aggregate = grouped.computeIfAbsent(event.service(), ignored -> new ServiceAggregate());
            aggregate.total++;
            if ("ERROR".equals(event.level())) aggregate.errors++;
            else aggregate.warnings++;
            aggregate.lastSeen = latest(aggregate.lastSeen, event.timestamp());
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, ServiceAggregate> entry) -> entry.getValue().total)
                        .reversed())
                .limit(5)
                .map(entry -> AdminStatisticsResponse.ServiceErrorItem.builder()
                        .service(entry.getKey())
                        .total(entry.getValue().total)
                        .warnings(entry.getValue().warnings)
                        .errors(entry.getValue().errors)
                        .lastSeen(entry.getValue().lastSeen)
                        .build())
                .toList();
    }

    private List<AdminStatisticsResponse.AffectedUserItem> buildTopUsers(List<ErrorEvent> events) {
        Map<String, UserAggregate> grouped = new HashMap<>();
        for (ErrorEvent event : events) {
            String key = event.userId() != null ? "id:" + event.userId()
                    : hasText(event.email()) ? "email:" + event.email() : "anonymous";
            UserAggregate aggregate = grouped.computeIfAbsent(key,
                    ignored -> new UserAggregate(event.userId(), event.email(), event.role()));
            aggregate.count++;
            aggregate.lastSeen = latest(aggregate.lastSeen, event.timestamp());
        }

        List<UUID> ids = grouped.values().stream().map(value -> value.userId)
                .filter(value -> value != null).distinct().toList();
        List<String> emails = grouped.values().stream().filter(value -> value.userId == null)
                .map(value -> value.email).filter(this::hasText).distinct().toList();
        Map<UUID, AccountSummaryProjection> byId = new HashMap<>();
        Map<String, AccountSummaryProjection> byEmail = new HashMap<>();
        if (!ids.isEmpty()) accountUserRepository.findAccountSummariesByIds(ids)
                .forEach(account -> byId.put(account.getId(), account));
        if (!emails.isEmpty()) accountUserRepository.findAccountSummariesByEmails(emails)
                .forEach(account -> byEmail.put(lower(account.getEmail()), account));

        return grouped.values().stream()
                .sorted(Comparator.comparingLong((UserAggregate value) -> value.count).reversed()
                        .thenComparing(value -> value.lastSeen, Comparator.reverseOrder()))
                .limit(5)
                .map(value -> {
                    AccountSummaryProjection account = value.userId != null
                            ? byId.get(value.userId) : byEmail.get(value.email);
                    boolean anonymous = account == null && !hasText(value.email);
                    return AdminStatisticsResponse.AffectedUserItem.builder()
                            .userId(account == null ? value.userId : account.getId())
                            .displayName(account == null
                                    ? (anonymous ? "Khách / Ẩn danh" : value.email)
                                    : account.getDisplayName())
                            .email(account == null ? value.email : account.getEmail())
                            .role(account == null ? value.role : account.getRole())
                            .avatarUrl(account == null ? null : resolveImageUrl(account.getImageUrl()))
                            .errorCount(value.count)
                            .lastSeen(value.lastSeen)
                            .anonymous(anonymous)
                            .build();
                }).toList();
    }

    private DateRange resolveRange(String requestedPeriod, Instant customFrom, Instant customTo) {
        String period = requestedPeriod == null ? "7D" : requestedPeriod.trim().toUpperCase(Locale.ROOT);
        ZoneId zone = dashboardZone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant from;
        Instant to = now.toInstant();
        boolean hourly;
        switch (period) {
            case "TODAY" -> { from = now.toLocalDate().atStartOfDay(zone).toInstant(); hourly = true; }
            case "7D" -> { from = now.toLocalDate().minusDays(6).atStartOfDay(zone).toInstant(); hourly = false; }
            case "30D" -> { from = now.toLocalDate().minusDays(29).atStartOfDay(zone).toInstant(); hourly = false; }
            case "CUSTOM" -> {
                if (customFrom == null || customTo == null || customFrom.isAfter(customTo)) {
                    throw badRequest("Khoảng thời gian tùy chọn không hợp lệ.");
                }
                if (customTo.isAfter(Instant.now().plusSeconds(60))) {
                    throw badRequest("Khoảng thời gian không thể vượt quá hiện tại.");
                }
                if (Duration.between(customFrom, customTo).compareTo(Duration.ofDays(90)) > 0) {
                    throw badRequest("Khoảng thời gian tùy chọn tối đa là 90 ngày.");
                }
                from = customFrom;
                to = customTo;
                LocalDate start = customFrom.atZone(zone).toLocalDate();
                LocalDate end = customTo.atZone(zone).toLocalDate();
                hourly = start.equals(end);
            }
            default -> throw badRequest("Period chỉ hỗ trợ TODAY, 7D, 30D hoặc CUSTOM.");
        }
        return new DateRange(period, from, to, hourly);
    }

    private JsonNode parseContext(String details) {
        if (!hasText(details)) return objectMapper.createObjectNode();
        int stackIndex = details.indexOf("Stack:");
        String json = stackIndex >= 0 ? details.substring(stackIndex + 6).trim() : details.trim();
        if (!json.startsWith("{")) return objectMapper.createObjectNode();
        try { return objectMapper.readTree(json); }
        catch (Exception ignored) { return objectMapper.createObjectNode(); }
    }

    private String normalizeEndpoint(String raw) {
        if (!hasText(raw)) return null;
        String path = raw;
        try {
            URI uri = URI.create(raw);
            if (hasText(uri.getPath())) path = uri.getPath();
        } catch (Exception ignored) {}
        path = UUID_PATTERN.matcher(path).replaceAll(":uuid");
        return NUMBER_SEGMENT_PATTERN.matcher(path).replaceAll(":id");
    }

    private String normalizeService(String raw) {
        if (!hasText(raw)) return "BACKEND";
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('_', '-');
        if ("FRONTEND".equals(value) || "AI-SERVICE".equals(value)) return value;
        return value.contains("AI") ? "AI-SERVICE" : value;
    }

    private String resolveImageUrl(String imageUrl) {
        if (!hasText(imageUrl) || imageUrl.startsWith("http")) return imageUrl;
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }

    private ZoneId dashboardZone() {
        try { return ZoneId.of(dashboardZoneId); }
        catch (Exception ignored) { return ZoneId.of("Asia/Ho_Chi_Minh"); }
    }

    private String bucketKey(Instant timestamp, boolean hourly, ZoneId zone) {
        ZonedDateTime date = timestamp.atZone(zone);
        return hourly ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
                : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.canConvertToInt() ? null : value.asInt();
    }
    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        try { return Instant.parse(String.valueOf(value)); }
        catch (Exception ignored) { return Instant.now(); }
    }
    private UUID uuid(String value) {
        try { return hasText(value) ? UUID.fromString(value) : null; }
        catch (Exception ignored) { return null; }
    }
    private Role role(String value) {
        try { return hasText(value) ? Role.valueOf(value.toUpperCase(Locale.ROOT)) : null; }
        catch (Exception ignored) { return null; }
    }
    private String lower(String value) { return hasText(value) ? value.toLowerCase(Locale.ROOT) : null; }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private Instant latest(Instant current, Instant candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
    private double roundOneDecimal(double value) { return Math.round(value * 10.0) / 10.0; }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private String firstNonBlank(String... values) {
        for (String value : values) if (hasText(value)) return value;
        return "";
    }
    @SafeVarargs private final <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }

    private record LogFetchResult(boolean available, boolean partial, List<Map<String, Object>> logs) {}
    private record TimeWindow(long fromMillis, long toMillis) {
        private long durationMillis() { return Math.max(0, toMillis - fromMillis); }
    }
    private record DateRange(String period, Instant from, Instant to, boolean hourly) {}
    private record ErrorEvent(String level, Instant timestamp, String method, String endpoint,
                              Integer statusCode, String service, String traceId, UUID userId,
                              String email, Role role) {}

    private static class MutableTrend {
        private final String key;
        private final String label;
        private long total;
        private long warnings;
        private long errors;
        private MutableTrend(String key, String label) { this.key = key; this.label = label; }
        private AdminStatisticsResponse.ErrorTrendPoint response() {
            return AdminStatisticsResponse.ErrorTrendPoint.builder()
                    .key(key).label(label).total(total).warnings(warnings).errors(errors).build();
        }
    }
    private static class ApiAggregate {
        private final String method;
        private final String endpoint;
        private final Map<Integer, Long> statusCounts = new HashMap<>();
        private long count;
        private Instant lastSeen;
        private ApiAggregate(String method, String endpoint) { this.method = method; this.endpoint = endpoint; }
    }
    private static class ServiceAggregate {
        private long total;
        private long warnings;
        private long errors;
        private Instant lastSeen;
    }
    private static class UserAggregate {
        private final UUID userId;
        private final String email;
        private final Role role;
        private long count;
        private Instant lastSeen;
        private UserAggregate(UUID userId, String email, Role role) {
            this.userId = userId; this.email = email; this.role = role;
        }
    }
}

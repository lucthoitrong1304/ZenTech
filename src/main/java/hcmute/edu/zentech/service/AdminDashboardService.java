package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import hcmute.edu.zentech.dto.response.AdminDashboardResponse;
import hcmute.edu.zentech.dto.response.AdminResourceMetricsResponse;
import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.repository.IncidentRepository;
import hcmute.edu.zentech.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {
    private static final List<TicketStatus> ACTIONABLE_TICKET_STATUSES =
            List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern TRACE_PATTERN = Pattern.compile("ZT-[A-Za-z0-9_-]+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final IncidentRepository incidentRepository;
    private final TicketRepository ticketRepository;
    private final AdminLogService adminLogService;
    private final ObjectMapper objectMapper;

    @Value("${app.dashboard.zone-id:Asia/Ho_Chi_Minh}")
    private String dashboardZoneId;

    public AdminDashboardResponse getDashboard(String requestedPeriod, Instant customFrom, Instant customTo) {
        DateRange range = resolveRange(requestedPeriod, customFrom, customTo);
        List<Incident> activeIncidents = incidentRepository.findByStatusNot(IncidentStatus.RESOLVED);
        List<Ticket> actionableTickets = ticketRepository.findByStatusIn(ACTIONABLE_TICKET_STATUSES);
        List<Incident> createdIncidents = incidentRepository.findByCreatedAtBetween(range.from(), range.to());
        List<Incident> resolvedIncidents = incidentRepository.findByResolvedAtBetween(range.from(), range.to());

        LogSnapshot logSnapshot = loadIssues(range);
        List<IssueAggregate> issues = logSnapshot.issues();
        List<AdminDashboardResponse.TrendPoint> trend = buildTrend(range, issues, createdIncidents, resolvedIncidents);

        long highPriorityIncidents = activeIncidents.stream()
                .filter(incident -> incident.getSeverity() == IncidentSeverity.CRITICAL
                        || incident.getSeverity() == IncidentSeverity.HIGH)
                .count();
        long unassignedIncidents = activeIncidents.stream().filter(this::isIncidentUnassigned).count();
        long unassignedTickets = actionableTickets.stream().filter(ticket -> ticket.getAssignee() == null).count();
        Instant staleCutoff = Instant.now().minus(Duration.ofHours(24));
        long staleTickets = actionableTickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null && ticket.getCreatedAt().isBefore(staleCutoff))
                .count();
        long averageResolutionMinutes = Math.round(resolvedIncidents.stream()
                .filter(incident -> incident.getResolvedAt() != null)
                .mapToLong(incident -> Duration.between(
                        firstNonNull(incident.getFirstOccurredAt(), incident.getCreatedAt()),
                        incident.getResolvedAt()
                ).toMinutes())
                .filter(minutes -> minutes >= 0)
                .average()
                .orElse(0));
        double resolutionRate = createdIncidents.isEmpty()
                ? 0
                : roundOneDecimal((resolvedIncidents.size() * 100.0) / createdIncidents.size());

        boolean hasCritical = activeIncidents.stream()
                .anyMatch(incident -> incident.getSeverity() == IncidentSeverity.CRITICAL);
        String health = hasCritical ? "CRITICAL"
                : (!activeIncidents.isEmpty() || !actionableTickets.isEmpty() || !issues.isEmpty())
                ? "DEGRADED" : "HEALTHY";

        return AdminDashboardResponse.builder()
                .period(range.period())
                .from(range.from())
                .to(range.to())
                .health(health)
                .generatedAt(Instant.now())
                .logsAvailable(logSnapshot.available())
                .metrics(AdminDashboardResponse.Metrics.builder()
                        .issuesInPeriod(issues.size())
                        .errorsInPeriod(issues.stream()
                                .filter(issue -> "ERROR".equals(issue.level))
                                .mapToLong(issue -> issue.occurrences)
                                .sum())
                        .openIncidents(activeIncidents.size())
                        .highPriorityIncidents(highPriorityIncidents)
                        .unassignedIncidents(unassignedIncidents)
                        .actionableTickets(actionableTickets.size())
                        .unassignedTickets(unassignedTickets)
                        .staleTickets(staleTickets)
                        .incidentsCreatedInPeriod(createdIncidents.size())
                        .incidentsResolvedInPeriod(resolvedIncidents.size())
                        .incidentResolutionRate(resolutionRate)
                        .averageResolutionMinutes(averageResolutionMinutes)
                        .build())
                .trend(trend)
                .topIssues(issues.stream().limit(5).map(this::toIssueItem).toList())
                .priorityIncidents(activeIncidents.stream()
                        .sorted(incidentPriorityComparator())
                        .limit(5)
                        .map(this::toIncidentItem)
                        .toList())
                .priorityTickets(actionableTickets.stream()
                        .sorted(ticketPriorityComparator())
                        .limit(5)
                        .map(this::toTicketItem)
                        .toList())
                .topServices(buildTopServices(issues))
                .build();
    }

    public AdminResourceMetricsResponse getResourceMetrics() {
        Instant generatedAt = Instant.now();
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            if (osBean == null) {
                throw new IllegalStateException("Operating system metrics are not supported by this JVM");
            }

            long totalRam = osBean.getTotalMemorySize();
            long freeRam = osBean.getFreeMemorySize();
            long usedRam = Math.max(0, totalRam - freeRam);
            double cpuLoad = osBean.getCpuLoad();

            Path applicationPath = Path.of("").toAbsolutePath().normalize();
            FileStore fileStore = Files.getFileStore(applicationPath);
            long totalDisk = fileStore.getTotalSpace();
            long usableDisk = fileStore.getUsableSpace();
            long usedDisk = Math.max(0, totalDisk - usableDisk);

            return AdminResourceMetricsResponse.builder()
                    .status("AVAILABLE")
                    .cpuUsagePercent(cpuLoad >= 0 ? roundTwoDecimals(cpuLoad * 100) : null)
                    .ramUsagePercent(percent(usedRam, totalRam))
                    .diskUsagePercent(percent(usedDisk, totalDisk))
                    .ramUsedBytes(usedRam)
                    .ramTotalBytes(totalRam)
                    .diskUsedBytes(usedDisk)
                    .diskTotalBytes(totalDisk)
                    .diskPath(applicationPath.getRoot() != null
                            ? applicationPath.getRoot().toString()
                            : applicationPath.toString())
                    .generatedAt(generatedAt)
                    .message(null)
                    .build();
        } catch (Exception exception) {
            log.warn("Unable to read host resource metrics", exception);
            return AdminResourceMetricsResponse.builder()
                    .status("UNAVAILABLE")
                    .generatedAt(generatedAt)
                    .message("Kh\u00F4ng th\u1EC3 \u0111\u1ECDc t\u00E0i nguy\u00EAn m\u00E1y ch\u1EE7 t\u1EA1i th\u1EDDi \u0111i\u1EC3m n\u00E0y.")
                    .build();
        }
    }

    private LogSnapshot loadIssues(DateRange range) {
        List<Map<String, Object>> logs = adminLogService.getLogs(
                "ALL", "", "", 5000, range.from().toEpochMilli(), range.to().toEpochMilli()
        );
        boolean available = logs.stream().noneMatch(log -> "OFFLINE-LOG".equals(String.valueOf(log.get("id"))));
        if (!available) {
            return new LogSnapshot(false, List.of());
        }

        Map<String, IssueAggregate> grouped = new LinkedHashMap<>();
        logs.stream()
                .filter(log -> "WARN".equalsIgnoreCase(stringValue(log.get("level")))
                        || "ERROR".equalsIgnoreCase(stringValue(log.get("level"))))
                .forEach(log -> {
                    String level = stringValue(log.get("level")).toUpperCase(Locale.ROOT);
                    String category = normalizeCategory(stringValue(log.get("category")));
                    String message = stringValue(log.get("message"));
                    String details = stringValue(log.get("details"));
                    Instant timestamp = toInstant(log.get("timestamp"));
                    ClientLogContext context = parseClientLogContext(details);
                    String baseMessage = normalizeIssueMessage(message);
                    String endpoint = context.apiPath() == null ? null : normalizeApiPath(context.apiPath());
                    String eventPart = hasText(context.eventType()) ? context.eventType() : baseMessage;
                    String apiPart = hasText(endpoint) ? endpoint : baseMessage;
                    String signature = level + ":" + category + ":" + eventPart + ":" + apiPart;
                    String title = hasText(context.eventType()) && hasText(endpoint)
                            ? friendlyJourneyTitle(context.eventType(), message) + " \u00B7 "
                            + (hasText(context.method()) ? context.method() : "HTTP") + " " + endpoint
                            : firstMessageSegment(message);

                    IssueAggregate aggregate = grouped.computeIfAbsent(signature,
                            key -> new IssueAggregate(signature, title, level, category, endpoint,
                                    0, timestamp, timestamp));
                    aggregate.occurrences++;
                    if (timestamp.isBefore(aggregate.firstSeen)) aggregate.firstSeen = timestamp;
                    if (timestamp.isAfter(aggregate.lastSeen)) aggregate.lastSeen = timestamp;
                });

        List<IssueAggregate> issues = new ArrayList<>(grouped.values());
        issues.sort(issuePriorityComparator());
        return new LogSnapshot(true, issues);
    }

    private List<AdminDashboardResponse.TrendPoint> buildTrend(
            DateRange range,
            List<IssueAggregate> issues,
            List<Incident> createdIncidents,
            List<Incident> resolvedIncidents
    ) {
        ZoneId zone = dashboardZone();
        boolean hourly = range.hourly();
        Map<String, MutableTrendPoint> buckets = new LinkedHashMap<>();
        ZonedDateTime cursor = range.from().atZone(zone);
        ZonedDateTime end = range.to().atZone(zone);
        if (hourly) cursor = cursor.withMinute(0).withSecond(0).withNano(0);
        else cursor = cursor.toLocalDate().atStartOfDay(zone);

        while (!cursor.isAfter(end)) {
            String key = bucketKey(cursor.toInstant(), hourly, zone);
            String label = hourly
                    ? cursor.format(DateTimeFormatter.ofPattern("HH:mm"))
                    : cursor.format(DateTimeFormatter.ofPattern("dd/MM"));
            buckets.put(key, new MutableTrendPoint(key, label));
            cursor = hourly ? cursor.plusHours(1) : cursor.plusDays(1);
        }

        issues.forEach(issue -> {
            MutableTrendPoint point = buckets.get(bucketKey(issue.lastSeen, hourly, zone));
            if (point != null) {
                point.issues++;
                if ("ERROR".equals(issue.level)) point.errors += issue.occurrences;
            }
        });
        createdIncidents.forEach(incident -> incrementCreated(buckets, incident.getCreatedAt(), hourly, zone));
        resolvedIncidents.forEach(incident -> incrementResolved(buckets, incident.getResolvedAt(), hourly, zone));

        return buckets.values().stream().map(MutableTrendPoint::toResponse).toList();
    }

    private List<AdminDashboardResponse.ServiceErrorItem> buildTopServices(List<IssueAggregate> issues) {
        Map<String, ServiceAggregate> services = new LinkedHashMap<>();
        for (IssueAggregate issue : issues) {
            String serviceKey = hasText(issue.endpoint)
                    ? issue.category + " \u00B7 " + issue.endpoint
                    : issue.category;
            ServiceAggregate aggregate = services.computeIfAbsent(serviceKey,
                    ignored -> new ServiceAggregate(serviceKey, 0, issue.title, issue.lastSeen));
            aggregate.occurrences += issue.occurrences;
            if (issue.lastSeen.isAfter(aggregate.lastSeen)) {
                aggregate.lastSeen = issue.lastSeen;
                aggregate.latestIssueTitle = issue.title;
            }
        }
        return services.values().stream()
                .sorted(Comparator.comparingLong((ServiceAggregate item) -> item.occurrences).reversed()
                        .thenComparing(item -> item.lastSeen, Comparator.reverseOrder()))
                .limit(5)
                .map(item -> AdminDashboardResponse.ServiceErrorItem.builder()
                        .service(item.service)
                        .occurrences(item.occurrences)
                        .latestIssueTitle(item.latestIssueTitle)
                        .lastSeen(item.lastSeen)
                        .build())
                .toList();
    }

    private AdminDashboardResponse.IssueItem toIssueItem(IssueAggregate issue) {
        return AdminDashboardResponse.IssueItem.builder()
                .signature(issue.signature)
                .title(issue.title)
                .level(issue.level)
                .category(issue.category)
                .occurrences(issue.occurrences)
                .firstSeen(issue.firstSeen)
                .lastSeen(issue.lastSeen)
                .build();
    }

    private AdminDashboardResponse.IncidentItem toIncidentItem(Incident incident) {
        return AdminDashboardResponse.IncidentItem.builder()
                .id(incident.getId())
                .code(incident.getCode())
                .title(firstNonBlank(incident.getErrorMessage(), incident.getApiPath(), "S\u1EF1 c\u1ED1 h\u1EC7 th\u1ED1ng"))
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .serviceName(incident.getServiceName())
                .apiPath(incident.getApiPath())
                .assignee(incident.getAssignee())
                .createdAt(incident.getCreatedAt())
                .firstOccurredAt(incident.getFirstOccurredAt())
                .build();
    }

    private AdminDashboardResponse.TicketItem toTicketItem(Ticket ticket) {
        String assigneeEmail = ticket.getAssignee() == null ? null : ticket.getAssignee().getEmail();
        return AdminDashboardResponse.TicketItem.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .title(ticket.getTitle())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .assigneeName(assigneeEmail)
                .assigneeEmail(assigneeEmail)
                .createdAt(ticket.getCreatedAt())
                .build();
    }

    private Comparator<Incident> incidentPriorityComparator() {
        return Comparator.comparingInt((Incident incident) -> severityRank(incident.getSeverity()))
                .thenComparing(incident -> isIncidentUnassigned(incident) ? 0 : 1)
                .thenComparing(incident -> firstNonNull(incident.getFirstOccurredAt(), incident.getCreatedAt()),
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<Ticket> ticketPriorityComparator() {
        return Comparator.comparingInt((Ticket ticket) -> priorityRank(ticket.getPriority()))
                .thenComparing(ticket -> ticket.getAssignee() == null ? 0 : 1)
                .thenComparing(Ticket::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<IssueAggregate> issuePriorityComparator() {
        return Comparator.comparingInt((IssueAggregate issue) -> "ERROR".equals(issue.level) ? 0 : 1)
                .thenComparing(Comparator.comparingLong((IssueAggregate issue) -> issue.occurrences).reversed())
                .thenComparing(issue -> issue.lastSeen, Comparator.reverseOrder());
    }

    private DateRange resolveRange(String requestedPeriod, Instant customFrom, Instant customTo) {
        String period = requestedPeriod == null ? "7D" : requestedPeriod.trim().toUpperCase(Locale.ROOT);
        ZoneId zone = dashboardZone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant from;
        Instant to = now.toInstant();
        boolean hourly;

        switch (period) {
            case "TODAY" -> {
                from = now.toLocalDate().atStartOfDay(zone).toInstant();
                hourly = true;
            }
            case "7D" -> {
                from = now.toLocalDate().minusDays(6).atStartOfDay(zone).toInstant();
                hourly = false;
            }
            case "30D" -> {
                from = now.toLocalDate().minusDays(29).atStartOfDay(zone).toInstant();
                hourly = false;
            }
            case "CUSTOM" -> {
                if (customFrom == null || customTo == null) {
                    throw badRequest("Kho\u1EA3ng th\u1EDDi gian t\u00F9y ch\u1ECDn c\u1EA7n c\u00F3 \u0111\u1EA7y \u0111\u1EE7 from v\u00E0 to.");
                }
                if (customFrom.isAfter(customTo)) {
                    throw badRequest("Th\u1EDDi gian b\u1EAFt \u0111\u1EA7u kh\u00F4ng th\u1EC3 sau th\u1EDDi gian k\u1EBFt th\u00FAc.");
                }
                if (customTo.isAfter(Instant.now().plusSeconds(60))) {
                    throw badRequest("Kho\u1EA3ng th\u1EDDi gian kh\u00F4ng th\u1EC3 v\u01B0\u1EE3t qu\u00E1 hi\u1EC7n t\u1EA1i.");
                }
                if (Duration.between(customFrom, customTo).compareTo(Duration.ofDays(90)) > 0) {
                    throw badRequest("Kho\u1EA3ng th\u1EDDi gian t\u00F9y ch\u1ECDn t\u1ED1i \u0111a l\u00E0 90 ng\u00E0y.");
                }
                from = customFrom;
                to = customTo;
                LocalDate startDate = customFrom.atZone(zone).toLocalDate();
                LocalDate endDate = customTo.atZone(zone).toLocalDate();
                hourly = startDate.equals(endDate);
            }
            default -> throw badRequest("Period ch\u1EC9 h\u1ED7 tr\u1EE3 TODAY, 7D, 30D ho\u1EB7c CUSTOM.");
        }
        return new DateRange(period, from, to, hourly);
    }

    private ClientLogContext parseClientLogContext(String details) {
        if (!hasText(details)) return ClientLogContext.EMPTY;
        int stackIndex = details.indexOf("Stack:");
        if (stackIndex < 0) return ClientLogContext.EMPTY;
        String rawJson = details.substring(stackIndex + "Stack:".length()).trim();
        if (!rawJson.startsWith("{")) return ClientLogContext.EMPTY;
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            return new ClientLogContext(
                    textValue(node, "eventType"),
                    textValue(node, "method"),
                    textValue(node, "apiPath")
            );
        } catch (Exception ignored) {
            return ClientLogContext.EMPTY;
        }
    }

    private String normalizeIssueMessage(String message) {
        String normalized = TRACE_PATTERN.matcher(stringValue(message)).replaceAll("ZT-*");
        normalized = UUID_PATTERN.matcher(normalized).replaceAll(":uuid");
        return NUMBER_PATTERN.matcher(normalized).replaceAll(":id").trim();
    }

    private String normalizeApiPath(String apiPath) {
        if (!hasText(apiPath)) return null;
        try {
            URI uri = URI.create(apiPath);
            return hasText(uri.getPath()) ? uri.getPath() : apiPath;
        } catch (Exception ignored) {
            return apiPath;
        }
    }

    private String friendlyJourneyTitle(String eventType, String fallback) {
        return switch (eventType) {
            case "HttpRequestSucceeded" -> "G\u1ECDi API th\u00E0nh c\u00F4ng";
            case "HttpRequestFailed" -> "G\u1ECDi API th\u1EA5t b\u1EA1i";
            case "RouteNavigated" -> "\u0110i\u1EC1u h\u01B0\u1EDBng trang";
            case "ProductViewed" -> "Xem s\u1EA3n ph\u1EA9m";
            case "CartItemAdded" -> "Th\u00EAm s\u1EA3n ph\u1EA9m v\u00E0o gi\u1ECF";
            case "AuthLoginSucceeded" -> "\u0110\u0103ng nh\u1EADp th\u00E0nh c\u00F4ng";
            case "AuthLoginFailed" -> "\u0110\u0103ng nh\u1EADp th\u1EA5t b\u1EA1i";
            case "RouteGuardDenied" -> "B\u1ECB ch\u1EB7n truy c\u1EADp";
            default -> firstNonBlank(eventType, firstMessageSegment(fallback), fallback);
        };
    }

    private String firstMessageSegment(String message) {
        if (!hasText(message)) return "V\u1EA5n \u0111\u1EC1 h\u1EC7 th\u1ED1ng";
        String[] segments = message.split("\\|", 2);
        return segments[0].trim();
    }

    private String normalizeCategory(String category) {
        String normalized = stringValue(category).trim().toUpperCase(Locale.ROOT).replace('_', '-');
        if ("FRONTEND".equals(normalized)) return "FRONTEND";
        if ("AI-SERVICE".equals(normalized)) return "AI-SERVICE";
        return "BACKEND";
    }

    private String bucketKey(Instant value, boolean hourly, ZoneId zone) {
        ZonedDateTime dateTime = value.atZone(zone);
        return hourly
                ? dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
                : dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private void incrementCreated(Map<String, MutableTrendPoint> buckets, Instant value, boolean hourly, ZoneId zone) {
        if (value == null) return;
        MutableTrendPoint point = buckets.get(bucketKey(value, hourly, zone));
        if (point != null) point.incidentsCreated++;
    }

    private void incrementResolved(Map<String, MutableTrendPoint> buckets, Instant value, boolean hourly, ZoneId zone) {
        if (value == null) return;
        MutableTrendPoint point = buckets.get(bucketKey(value, hourly, zone));
        if (point != null) point.incidentsResolved++;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private int severityRank(IncidentSeverity severity) {
        if (severity == IncidentSeverity.CRITICAL) return 0;
        if (severity == IncidentSeverity.HIGH) return 1;
        if (severity == IncidentSeverity.MEDIUM) return 2;
        return 3;
    }

    private int priorityRank(TicketPriority priority) {
        if (priority == TicketPriority.CRITICAL) return 0;
        if (priority == TicketPriority.HIGH) return 1;
        if (priority == TicketPriority.MEDIUM) return 2;
        return 3;
    }

    private boolean isIncidentUnassigned(Incident incident) {
        return !hasText(incident.getAssignee());
    }

    private ZoneId dashboardZone() {
        try {
            return ZoneId.of(dashboardZoneId);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (hasText(value)) return value;
        return "";
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }

    private Double percent(long used, long total) {
        return total <= 0 ? null : roundTwoDecimals((used * 100.0) / total);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record DateRange(String period, Instant from, Instant to, boolean hourly) {}
    private record LogSnapshot(boolean available, List<IssueAggregate> issues) {}
    private record ClientLogContext(String eventType, String method, String apiPath) {
        private static final ClientLogContext EMPTY = new ClientLogContext(null, null, null);
    }

    private static class IssueAggregate {
        private final String signature;
        private final String title;
        private final String level;
        private final String category;
        private final String endpoint;
        private long occurrences;
        private Instant firstSeen;
        private Instant lastSeen;

        private IssueAggregate(String signature, String title, String level, String category, String endpoint,
                               long occurrences, Instant firstSeen, Instant lastSeen) {
            this.signature = signature;
            this.title = title;
            this.level = level;
            this.category = category;
            this.endpoint = endpoint;
            this.occurrences = occurrences;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }
    }

    private static class ServiceAggregate {
        private final String service;
        private long occurrences;
        private String latestIssueTitle;
        private Instant lastSeen;

        private ServiceAggregate(String service, long occurrences, String latestIssueTitle, Instant lastSeen) {
            this.service = service;
            this.occurrences = occurrences;
            this.latestIssueTitle = latestIssueTitle;
            this.lastSeen = lastSeen;
        }
    }

    private static class MutableTrendPoint {
        private final String key;
        private final String label;
        private long issues;
        private long errors;
        private long incidentsCreated;
        private long incidentsResolved;

        private MutableTrendPoint(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private AdminDashboardResponse.TrendPoint toResponse() {
            return AdminDashboardResponse.TrendPoint.builder()
                    .key(key)
                    .label(label)
                    .issues(issues)
                    .errors(errors)
                    .incidentsCreated(incidentsCreated)
                    .incidentsResolved(incidentsResolved)
                    .build();
        }
    }
}

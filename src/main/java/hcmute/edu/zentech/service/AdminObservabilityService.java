package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AdminObservabilityResponse;
import hcmute.edu.zentech.monitoring.HostResourceMetricsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class AdminObservabilityService {
    private static final double SLOW_API_AVERAGE_THRESHOLD_MS = 500;
    private static final String HEAP_PERCENT = "100 * sum(jvm_memory_used_bytes{area=\"heap\"}) / sum(jvm_memory_max_bytes{area=\"heap\"} > 0)";
    private static final String REQUEST_RATE = "sum(rate(http_server_requests_seconds_count{uri!=\"/actuator/prometheus\"}[5m])) * 60";
    private static final String ERROR_RATE = "100 * (sum(rate(http_server_requests_seconds_count{uri!=\"/actuator/prometheus\",status=~\"4..|5..\"}[5m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{uri!=\"/actuator/prometheus\"}[5m])), 0.000001)";
    private static final String AVERAGE_LATENCY = "1000 * sum(rate(http_server_requests_seconds_sum{uri!=\"/actuator/prometheus\"}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count{uri!=\"/actuator/prometheus\"}[5m])), 0.000001)";
    private static final String P95_LATENCY = "1000 * histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri!=\"/actuator/prometheus\"}[5m])))";

    private final PrometheusQueryService prometheus;
    private final HostResourceMetricsProvider hostMetrics;
    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${app.loki.url:http://localhost:3100}") private String lokiUrl;
    @Value("${app.qdrant.url:http://localhost:6333}") private String qdrantUrl;
    @Value("${app.alloy.url:http://localhost:12345}") private String alloyUrl;
    @Value("${app.dashboard.zone-id:Asia/Ho_Chi_Minh}") private String dashboardZoneId;

    public AdminObservabilityResponse getObservability(String requestedPeriod, Instant customFrom, Instant customTo) {
        DateRange range = resolveRange(requestedPeriod, customFrom, customTo);
        long step = Math.max(15, Math.max(1, Duration.between(range.from(), range.to()).getSeconds()) / 160);
        HostResourceMetricsProvider.HostResourceSnapshot host = hostMetrics.snapshot();
        boolean prometheusAvailable = prometheus.isReady();

        Double heapUsed = prometheus.queryScalar("sum(jvm_memory_used_bytes{area=\"heap\"})");
        Double heapMax = prometheus.queryScalar("sum(jvm_memory_max_bytes{area=\"heap\"} > 0)");
        Double heapPercent = percent(heapUsed, heapMax);
        Double processCpu = multiply(prometheus.queryScalar("process_cpu_usage"), 100);

        Map<Long, MutableHistory> history = new LinkedHashMap<>();
        merge(history, prometheus.queryRangeSafe("zentech_host_cpu_usage_percent", range.from(), range.to(), step), Metric.CPU);
        merge(history, prometheus.queryRangeSafe("zentech_host_ram_usage_percent", range.from(), range.to(), step), Metric.RAM);
        merge(history, prometheus.queryRangeSafe("zentech_host_disk_usage_percent", range.from(), range.to(), step), Metric.DISK);
        merge(history, prometheus.queryRangeSafe(HEAP_PERCENT, range.from(), range.to(), step), Metric.HEAP);
        merge(history, prometheus.queryRangeSafe(REQUEST_RATE, range.from(), range.to(), step), Metric.REQUEST_RATE);
        merge(history, prometheus.queryRangeSafe(ERROR_RATE, range.from(), range.to(), step), Metric.ERROR_RATE);
        merge(history, prometheus.queryRangeSafe(P95_LATENCY, range.from(), range.to(), step), Metric.P95);
        List<AdminObservabilityResponse.MetricHistoryPoint> historyPoints = history.values().stream()
                .map(MutableHistory::toResponse)
                .sorted(Comparator.comparing(AdminObservabilityResponse.MetricHistoryPoint::getTimestamp))
                .toList();

        return AdminObservabilityResponse.builder()
                .period(range.period()).from(range.from()).to(range.to()).generatedAt(Instant.now())
                .prometheusAvailable(prometheusAvailable)
                .health(AdminObservabilityResponse.HealthOverview.builder()
                        .cpuUsagePercent(host.cpuUsagePercent())
                        .ramUsagePercent(host.ramUsagePercent())
                        .diskUsagePercent(host.diskUsagePercent())
                        .jvmHeapUsagePercent(heapPercent)
                        .jvmHeapUsedBytes(toLong(heapUsed)).jvmHeapMaxBytes(toLong(heapMax))
                        .processCpuUsagePercent(processCpu)
                        .processUptimeSeconds(toLong(prometheus.queryScalar("process_uptime_seconds")))
                        .liveThreads(prometheus.queryScalar("jvm_threads_live_threads"))
                        .peakThreads(prometheus.queryScalar("jvm_threads_peak_threads"))
                        .build())
                .api(AdminObservabilityResponse.ApiPerformance.builder()
                        .requestsPerMinute(prometheus.queryScalar(REQUEST_RATE))
                        .errorRatePercent(prometheus.queryScalar(ERROR_RATE))
                        .p95LatencyMs(prometheus.queryScalar(P95_LATENCY))
                        .averageLatencyMs(prometheus.queryScalar(AVERAGE_LATENCY))
                        .activeRequests(prometheus.queryScalar("sum(http_server_requests_active_seconds_count)"))
                        .build())
                .history(historyPoints)
                .slowApis(querySlowApis())
                .errorApis(queryErrorApis())
                .thresholdEvents(buildThresholdEvents(historyPoints))
                .dependencies(checkDependencies(prometheusAvailable))
                .build();
    }

    private List<AdminObservabilityResponse.ApiAnomaly> querySlowApis() {
        String query = """
                topk(5,
                  (
                    (
                      1000 * sum by (method,uri) (
                        increase(http_server_requests_seconds_sum{
                          uri!="/actuator/prometheus",
                          uri!="/api/admin/dashboard/observability",
                          method!="OPTIONS"
                        }[15m])
                      )
                      /
                      clamp_min(
                        sum by (method,uri) (
                          increase(http_server_requests_seconds_count{
                            uri!="/actuator/prometheus",
                            uri!="/api/admin/dashboard/observability",
                            method!="OPTIONS"
                          }[15m])
                        ),
                        1
                      )
                    )
                    and on(method,uri)
                    (
                      sum by (method,uri) (
                        increase(http_server_requests_seconds_count{
                          uri!="/actuator/prometheus",
                          uri!="/api/admin/dashboard/observability",
                          method!="OPTIONS"
                        }[15m])
                      ) >= 3
                    )
                  )
                )
                """;
        return prometheus.queryVectorSafe(query).stream()
                .filter(item -> item.value() >= SLOW_API_AVERAGE_THRESHOLD_MS)
                .map(item -> AdminObservabilityResponse.ApiAnomaly.builder()
                        .method(item.labels().getOrDefault("method", "HTTP"))
                        .uri(item.labels().getOrDefault("uri", "unknown"))
                        .value(item.value()).unit("ms trung bình").build())
                .toList();
    }

    private List<AdminObservabilityResponse.ApiAnomaly> queryErrorApis() {
        String query = """
                topk(5,
                  sum by (method,uri,status) (
                    increase(http_server_requests_seconds_count{
                      uri!="/actuator/prometheus",
                      uri!="/api/admin/dashboard/observability",
                      method!="OPTIONS",
                      status=~"4..|5.."
                    }[15m])
                  )
                )
                """;
        return prometheus.queryVectorSafe(query).stream()
                .filter(item -> item.value() > 0)
                .map(item -> AdminObservabilityResponse.ApiAnomaly.builder()
                        .method(item.labels().getOrDefault("method", "HTTP"))
                        .uri(item.labels().getOrDefault("uri", "unknown"))
                        .status(item.labels().get("status"))
                        .value(Math.rint(item.value())).unit("lỗi").build())
                .toList();
    }
    private List<AdminObservabilityResponse.ThresholdEvent> buildThresholdEvents(
            List<AdminObservabilityResponse.MetricHistoryPoint> history
    ) {
        List<AdminObservabilityResponse.ThresholdEvent> events = new ArrayList<>();
        Map<String, Boolean> above = new LinkedHashMap<>();
        for (AdminObservabilityResponse.MetricHistoryPoint point : history) {
            threshold(events, above, point.getTimestamp(), "CPU", point.getCpuUsagePercent(), 75);
            threshold(events, above, point.getTimestamp(), "RAM", point.getRamUsagePercent(), 75);
            threshold(events, above, point.getTimestamp(), "Disk", point.getDiskUsagePercent(), 75);
            threshold(events, above, point.getTimestamp(), "JVM Heap", point.getJvmHeapUsagePercent(), 75);
            threshold(events, above, point.getTimestamp(), "Error rate", point.getErrorRatePercent(), 5);
            threshold(events, above, point.getTimestamp(), "P95 latency", point.getP95LatencyMs(), 1000);
        }
        return events.stream().sorted(Comparator.comparing(AdminObservabilityResponse.ThresholdEvent::getTimestamp).reversed())
                .limit(10).toList();
    }

    private void threshold(List<AdminObservabilityResponse.ThresholdEvent> events, Map<String, Boolean> above,
                           Instant timestamp, String metric, Double value, double warning) {
        boolean isAbove = value != null && value >= warning;
        if (isAbove && !above.getOrDefault(metric, false)) {
            double critical = metric.equals("Error rate") ? 15 : metric.equals("P95 latency") ? 2500 : 90;
            events.add(AdminObservabilityResponse.ThresholdEvent.builder()
                    .timestamp(timestamp).metric(metric).value(value).threshold(warning)
                    .severity(value >= critical ? "CRITICAL" : "WARNING").build());
        }
        above.put(metric, isAbove);
    }

    private List<AdminObservabilityResponse.DependencyStatus> checkDependencies(boolean prometheusAvailable) {
        List<AdminObservabilityResponse.DependencyStatus> result = new ArrayList<>();
        result.add(dependency("Prometheus", prometheusAvailable, prometheusAvailable ? "Đang thu thập metric" : "Không thể kết nối", null, null, null, null));

        boolean mysqlUp = checkMysql();
        result.add(dependency("MySQL", mysqlUp, mysqlUp ? "Pool kết nối sẵn sàng" : "Kết nối database thất bại",
                prometheus.queryScalar("hikaricp_connections_active"), "active",
                prometheus.queryScalar("hikaricp_connections_idle"), "idle"));

        boolean rabbitUp = checkRabbit();
        result.add(dependency("RabbitMQ", rabbitUp, rabbitUp ? "Broker connection hoạt động" : "Không thể kết nối broker",
                prometheus.queryScalar("rabbitmq_connections"), "connections",
                prometheus.queryScalar("rabbitmq_channels"), "channels"));

        boolean lokiUp = checkHttpReady(lokiUrl + "/ready");
        result.add(dependency("Loki", lokiUp, lokiUp ? "Log storage sẵn sàng" : "Loki không phản hồi", null, null, null, null));

        boolean qdrantUp = checkHttpReady(qdrantUrl + "/readyz");
        result.add(dependency("Qdrant", qdrantUp, qdrantUp ? "Vector database sẵn sàng" : "Qdrant không phản hồi", null, null, null, null));

        boolean alloyUp = checkHttpReady(alloyUrl + "/-/ready");
        result.add(dependency("Alloy", alloyUp, alloyUp ? "Đang thu thập telemetry" : "Ngừng thu thập telemetry", null, null, null, null));
        return result;
    }

    private boolean checkMysql() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            return false;
        }
    }

    private boolean checkRabbit() {
        Connection connection = null;
        try {
            connection = rabbitConnectionFactory.createConnection();
            return connection.isOpen();
        } catch (Exception exception) {
            return false;
        } finally {
            if (connection != null) connection.close();
        }
    }

    private boolean checkHttpReady(String url) {
        try {
            RestTemplate restTemplate = restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(2)).build();
            return restTemplate.getForEntity(url, String.class).getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            return false;
        }
    }

    private AdminObservabilityResponse.DependencyStatus dependency(String name, boolean up, String detail,
            Double primary, String primaryUnit, Double secondary, String secondaryUnit) {
        return AdminObservabilityResponse.DependencyStatus.builder().name(name).status(up ? "UP" : "DOWN")
                .detail(detail).primaryValue(primary).primaryUnit(primaryUnit)
                .secondaryValue(secondary).secondaryUnit(secondaryUnit).build();
    }

    private void merge(Map<Long, MutableHistory> points, List<PrometheusQueryService.Sample> samples, Metric metric) {
        for (PrometheusQueryService.Sample sample : samples) {
            MutableHistory point = points.computeIfAbsent(sample.timestamp(), MutableHistory::new);
            point.set(metric, sample.value());
        }
    }

    private DateRange resolveRange(String requestedPeriod, Instant customFrom, Instant customTo) {
        String period = requestedPeriod == null ? "7D" : requestedPeriod.trim().toUpperCase(Locale.ROOT);
        ZoneId zone = dashboardZone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant from;
        Instant to = now.toInstant();
        switch (period) {
            case "TODAY" -> from = now.toLocalDate().atStartOfDay(zone).toInstant();
            case "7D" -> from = now.toLocalDate().minusDays(6).atStartOfDay(zone).toInstant();
            case "30D" -> from = now.toLocalDate().minusDays(29).atStartOfDay(zone).toInstant();
            case "CUSTOM" -> {
                if (customFrom == null || customTo == null || customFrom.isAfter(customTo)) throw badRequest("Khoảng thời gian tùy chọn không hợp lệ.");
                if (Duration.between(customFrom, customTo).compareTo(Duration.ofDays(90)) > 0) throw badRequest("Khoảng thời gian tối đa là 90 ngày.");
                from = customFrom; to = customTo;
            }
            default -> throw badRequest("Period chỉ hỗ trợ TODAY, 7D, 30D hoặc CUSTOM.");
        }
        return new DateRange(period, from, to);
    }

    private ZoneId dashboardZone() {
        try { return ZoneId.of(dashboardZoneId); } catch (Exception ignored) { return ZoneId.of("Asia/Ho_Chi_Minh"); }
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(BAD_REQUEST, message); }
    private Double percent(Double used, Double max) { return used == null || max == null || max <= 0 ? null : used * 100 / max; }
    private Double multiply(Double value, double factor) { return value == null ? null : value * factor; }
    private Long toLong(Double value) { return value == null ? null : Math.round(value); }

    private record DateRange(String period, Instant from, Instant to) {}
    private enum Metric { CPU, RAM, DISK, HEAP, REQUEST_RATE, ERROR_RATE, P95 }

    private static class MutableHistory {
        private final long timestamp;
        private Double cpu, ram, disk, heap, requestRate, errorRate, p95;
        private MutableHistory(long timestamp) { this.timestamp = timestamp; }
        private void set(Metric metric, double value) {
            switch (metric) {
                case CPU -> cpu = value; case RAM -> ram = value; case DISK -> disk = value;
                case HEAP -> heap = value; case REQUEST_RATE -> requestRate = value;
                case ERROR_RATE -> errorRate = value; case P95 -> p95 = value;
            }
        }
        private AdminObservabilityResponse.MetricHistoryPoint toResponse() {
            return AdminObservabilityResponse.MetricHistoryPoint.builder().timestamp(Instant.ofEpochSecond(timestamp))
                    .cpuUsagePercent(cpu).ramUsagePercent(ram).diskUsagePercent(disk).jvmHeapUsagePercent(heap)
                    .requestsPerMinute(requestRate).errorRatePercent(errorRate).p95LatencyMs(p95).build();
        }
    }
}

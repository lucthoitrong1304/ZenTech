package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AdminDependencyDetailResponse;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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
    private final org.springframework.core.env.Environment environment;

    @Value("${app.loki.url:http://localhost:3100}") private String lokiUrl;
    @Value("${app.qdrant.url:http://localhost:6333}") private String qdrantUrl;
    @Value("${app.alloy.url:http://localhost:12345}") private String alloyUrl;
    @Value("${app.dashboard.zone-id:Asia/Ho_Chi_Minh}") private String dashboardZoneId;
    @Value("${app.ai.base-url:http://localhost:8000}") private String aiBaseUrl;

    public AdminObservabilityResponse getObservability(String requestedPeriod, Instant customFrom, Instant customTo) {
        DateRange range = resolveRange(requestedPeriod, customFrom, customTo);
        long step = Math.max(15, Math.max(1, Duration.between(range.from(), range.to()).getSeconds()) / 160);
        HostResourceMetricsProvider.HostResourceSnapshot host = hostMetrics.snapshot();
        boolean prometheusAvailable = prometheus.isReady();

        CompletableFuture<Double> heapUsedFuture;
        CompletableFuture<Double> heapMaxFuture;
        CompletableFuture<Double> processCpuFuture;
        CompletableFuture<Double> processUptimeFuture;
        CompletableFuture<Double> liveThreadsFuture;
        CompletableFuture<Double> peakThreadsFuture;

        CompletableFuture<Double> promCpuFuture;
        CompletableFuture<Double> promCoresFuture;
        CompletableFuture<Double> promRamFuture;
        CompletableFuture<Double> promRamUsedFuture;
        CompletableFuture<Double> promRamTotalFuture;
        CompletableFuture<Double> promDiskFuture;
        CompletableFuture<Double> promDiskUsedFuture;
        CompletableFuture<Double> promDiskTotalFuture;

        CompletableFuture<Double> requestsPerMinuteFuture;
        CompletableFuture<Double> errorRateFuture;
        CompletableFuture<Double> p95LatencyFuture;
        CompletableFuture<Double> averageLatencyFuture;
        CompletableFuture<Double> activeRequestsFuture;

        CompletableFuture<List<AdminObservabilityResponse.ApiAnomaly>> slowApisFuture;
        CompletableFuture<List<AdminObservabilityResponse.ApiAnomaly>> errorApisFuture;
        CompletableFuture<List<AdminObservabilityResponse.DependencyStatus>> dependenciesFuture;

        CompletableFuture<List<PrometheusQueryService.Sample>> cpuHistoryFuture;
        CompletableFuture<List<PrometheusQueryService.Sample>> ramHistoryFuture;
        CompletableFuture<List<PrometheusQueryService.Sample>> diskHistoryFuture;
        CompletableFuture<List<PrometheusQueryService.Sample>> heapHistoryFuture;
        CompletableFuture<List<PrometheusQueryService.Sample>> requestHistoryFuture;
        CompletableFuture<List<PrometheusQueryService.Sample>> errorHistoryFuture;
        CompletableFuture<List<PrometheusQueryService.Sample>> p95HistoryFuture;

        if (prometheusAvailable) {
            heapUsedFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("sum(jvm_memory_used_bytes{area=\"heap\"})"));
            heapMaxFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("sum(jvm_memory_max_bytes{area=\"heap\"} > 0)"));
            processCpuFuture = CompletableFuture.supplyAsync(() -> multiply(prometheus.queryScalar("process_cpu_usage"), 100));
            processUptimeFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("process_uptime_seconds"));
            liveThreadsFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("jvm_threads_live_threads"));
            peakThreadsFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("jvm_threads_peak_threads"));

            promCpuFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_cpu_usage_percent"));
            promCoresFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_cpu_cores"));
            promRamFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_ram_usage_percent"));
            promRamUsedFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_ram_used_bytes"));
            promRamTotalFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_ram_total_bytes"));
            promDiskFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_disk_usage_percent"));
            promDiskUsedFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_disk_used_bytes"));
            promDiskTotalFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("zentech_host_disk_total_bytes"));

            requestsPerMinuteFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar(REQUEST_RATE));
            errorRateFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar(ERROR_RATE));
            p95LatencyFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar(P95_LATENCY));
            averageLatencyFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar(AVERAGE_LATENCY));
            activeRequestsFuture = CompletableFuture.supplyAsync(() -> prometheus.queryScalar("sum(http_server_requests_active_seconds_count)"));

            slowApisFuture = CompletableFuture.supplyAsync(this::querySlowApis);
            errorApisFuture = CompletableFuture.supplyAsync(this::queryErrorApis);
            dependenciesFuture = CompletableFuture.supplyAsync(() -> checkDependencies(true));

            cpuHistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe("zentech_host_cpu_usage_percent", range.from(), range.to(), step));
            ramHistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe("zentech_host_ram_usage_percent", range.from(), range.to(), step));
            diskHistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe("zentech_host_disk_usage_percent", range.from(), range.to(), step));
            heapHistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe(HEAP_PERCENT, range.from(), range.to(), step));
            requestHistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe(REQUEST_RATE, range.from(), range.to(), step));
            errorHistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe(ERROR_RATE, range.from(), range.to(), step));
            p95HistoryFuture = CompletableFuture.supplyAsync(() -> prometheus.queryRangeSafe(P95_LATENCY, range.from(), range.to(), step));
        } else {
            heapUsedFuture = CompletableFuture.completedFuture(null);
            heapMaxFuture = CompletableFuture.completedFuture(null);
            processCpuFuture = CompletableFuture.completedFuture(null);
            processUptimeFuture = CompletableFuture.completedFuture(null);
            liveThreadsFuture = CompletableFuture.completedFuture(null);
            peakThreadsFuture = CompletableFuture.completedFuture(null);

            promCpuFuture = CompletableFuture.completedFuture(null);
            promCoresFuture = CompletableFuture.completedFuture(null);
            promRamFuture = CompletableFuture.completedFuture(null);
            promRamUsedFuture = CompletableFuture.completedFuture(null);
            promRamTotalFuture = CompletableFuture.completedFuture(null);
            promDiskFuture = CompletableFuture.completedFuture(null);
            promDiskUsedFuture = CompletableFuture.completedFuture(null);
            promDiskTotalFuture = CompletableFuture.completedFuture(null);

            requestsPerMinuteFuture = CompletableFuture.completedFuture(null);
            errorRateFuture = CompletableFuture.completedFuture(null);
            p95LatencyFuture = CompletableFuture.completedFuture(null);
            averageLatencyFuture = CompletableFuture.completedFuture(null);
            activeRequestsFuture = CompletableFuture.completedFuture(null);

            slowApisFuture = CompletableFuture.completedFuture(List.of());
            errorApisFuture = CompletableFuture.completedFuture(List.of());
            dependenciesFuture = CompletableFuture.supplyAsync(() -> checkDependencies(false));

            cpuHistoryFuture = CompletableFuture.completedFuture(List.of());
            ramHistoryFuture = CompletableFuture.completedFuture(List.of());
            diskHistoryFuture = CompletableFuture.completedFuture(List.of());
            heapHistoryFuture = CompletableFuture.completedFuture(List.of());
            requestHistoryFuture = CompletableFuture.completedFuture(List.of());
            errorHistoryFuture = CompletableFuture.completedFuture(List.of());
            p95HistoryFuture = CompletableFuture.completedFuture(List.of());
        }

        Double heapUsed = heapUsedFuture.join();
        Double heapMax = heapMaxFuture.join();
        Double heapPercent = percent(heapUsed, heapMax);
        Double processCpu = processCpuFuture.join();

        Map<Long, MutableHistory> history = new LinkedHashMap<>();
        merge(history, cpuHistoryFuture.join(), Metric.CPU);
        merge(history, ramHistoryFuture.join(), Metric.RAM);
        merge(history, diskHistoryFuture.join(), Metric.DISK);
        merge(history, heapHistoryFuture.join(), Metric.HEAP);
        merge(history, requestHistoryFuture.join(), Metric.REQUEST_RATE);
        merge(history, errorHistoryFuture.join(), Metric.ERROR_RATE);
        merge(history, p95HistoryFuture.join(), Metric.P95);

        List<AdminObservabilityResponse.MetricHistoryPoint> historyPoints = history.values().stream()
                .map(MutableHistory::toResponse)
                .sorted(Comparator.comparing(AdminObservabilityResponse.MetricHistoryPoint::getTimestamp))
                .toList();

        Double cpu = host.cpuUsagePercent();
        Long cores = host.cpuCoreCount() != null ? host.cpuCoreCount().longValue() : null;
        Double ram = host.ramUsagePercent();
        Long ramUsed = host.ramUsedBytes();
        Long ramTotal = host.ramTotalBytes();
        Double disk = host.diskUsagePercent();
        Long diskUsed = host.diskUsedBytes();
        Long diskTotal = host.diskTotalBytes();

        if (prometheusAvailable) {
            Double promCpu = promCpuFuture.join();
            if (promCpu != null) cpu = promCpu;
            Double promCores = promCoresFuture.join();
            if (promCores != null) cores = promCores.longValue();
            Double promRam = promRamFuture.join();
            if (promRam != null) ram = promRam;
            Double promRamUsed = promRamUsedFuture.join();
            if (promRamUsed != null) ramUsed = promRamUsed.longValue();
            Double promRamTotal = promRamTotalFuture.join();
            if (promRamTotal != null) ramTotal = promRamTotal.longValue();
            Double promDisk = promDiskFuture.join();
            if (promDisk != null) disk = promDisk;
            Double promDiskUsed = promDiskUsedFuture.join();
            if (promDiskUsed != null) diskUsed = promDiskUsed.longValue();
            Double promDiskTotal = promDiskTotalFuture.join();
            if (promDiskTotal != null) diskTotal = promDiskTotal.longValue();
        }

        return AdminObservabilityResponse.builder()
                .period(range.period()).from(range.from()).to(range.to()).generatedAt(Instant.now())
                .prometheusAvailable(prometheusAvailable)
                .health(AdminObservabilityResponse.HealthOverview.builder()
                        .cpuUsagePercent(cpu)
                        .cpuCoreCount(cores)
                        .ramUsagePercent(ram)
                        .ramUsedBytes(ramUsed)
                        .ramTotalBytes(ramTotal)
                        .diskUsagePercent(disk)
                        .diskUsedBytes(diskUsed)
                        .diskTotalBytes(diskTotal)
                        .jvmHeapUsagePercent(heapPercent)
                        .jvmHeapUsedBytes(toLong(heapUsed)).jvmHeapMaxBytes(toLong(heapMax))
                        .processCpuUsagePercent(processCpu)
                        .processUptimeSeconds(toLong(processUptimeFuture.join()))
                        .liveThreads(liveThreadsFuture.join())
                        .peakThreads(peakThreadsFuture.join())
                        .build())
                .api(AdminObservabilityResponse.ApiPerformance.builder()
                        .requestsPerMinute(requestsPerMinuteFuture.join())
                        .errorRatePercent(errorRateFuture.join())
                        .p95LatencyMs(p95LatencyFuture.join())
                        .averageLatencyMs(averageLatencyFuture.join())
                        .activeRequests(activeRequestsFuture.join())
                        .build())
                .history(historyPoints)
                .slowApis(slowApisFuture.join())
                .errorApis(errorApisFuture.join())
                .thresholdEvents(buildThresholdEvents(historyPoints))
                .dependencies(dependenciesFuture.join())
                .build();
    }

    private List<AdminObservabilityResponse.ApiAnomaly> querySlowApis() {
        String query = """
                topk(10,
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
                topk(10,
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

    public AdminDependencyDetailResponse getDependencyDetail(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mysql" -> mysqlDetail();
            case "rabbitmq", "rabbit" -> rabbitDetail();
            case "qdrant" -> httpDependencyDetail("Qdrant", "Business service", qdrantUrl, "/readyz",
                    checkHttpReady(qdrantUrl + "/readyz"), "Độ sẵn sàng của cơ sở dữ liệu Vector",
                    List.of(config("app.qdrant.url")), List.of(),
                    List.of("Lưu trữ dữ liệu tìm kiếm vector/AI phục vụ cho các tính năng gợi ý thông minh sản phẩm."));
            case "zentech ai", "zentech-ai" -> httpDependencyDetail("ZenTech AI", "Business service", aiBaseUrl, "/health",
                    checkHttpReady(aiBaseUrl + "/health"), "Độ sẵn sàng của dịch vụ AI",
                    List.of(config("app.ai.base-url")), List.of(),
                    List.of("Cung cấp các tính năng trí tuệ nhân tạo, xử lý ngôn ngữ tự nhiên và trợ lý ảo tư vấn khách hàng."));
            case "zentech fe", "zentech-fe", "fe" -> httpDependencyDetail("ZenTech FE", "Client Browser", "Trình duyệt Web Client", "",
                    true, "Đang truy cập",
                    List.of(
                            configItem("Framework", "Angular 21"),
                            configItem("Ngôn ngữ", "TypeScript"),
                            configItem("Bố cục UI", "Tailwind CSS & PrimeNG")
                    ), List.of(),
                    List.of("Giao diện SPA (Single Page Application) phía người dùng phục vụ trang Admin và trang mua sắm."));
            case "zentech java be", "zentech-be", "zentech be", "be" -> httpDependencyDetail("ZenTech Java BE", "Core API Service", "Dịch vụ Core API", "",
                    true, "Hoạt động tốt",
                    List.of(
                            configItem("Framework", "Spring Boot 3.4"),
                            configItem("Java Version", "JDK 21"),
                            configItem("Cổng dịch vụ", environment.getProperty("server.port", "8080"))
                    ), List.of(),
                    List.of("Dịch vụ backend lõi điều hướng, kết nối cơ sở dữ liệu, quản lý xác thực và điều phối toàn bộ nghiệp vụ."));
            case "prometheus" -> prometheusDetail();
            case "loki" -> httpDependencyDetail("Loki", "Observability infrastructure", lokiUrl, "/ready",
                    checkHttpReady(lokiUrl + "/ready"), "Độ sẵn sàng của kho lưu trữ nhật ký (Log)",
                    List.of(config("app.loki.url")), List.of(),
                    List.of("Lưu trữ nhật ký hoạt động (application logs) của ứng dụng để phục vụ điều tra lỗi và trang nhật ký admin."));
            case "alloy" -> httpDependencyDetail("Alloy", "Observability infrastructure", alloyUrl, "/-/ready",
                    checkHttpReady(alloyUrl + "/-/ready"), "Độ sẵn sàng của bộ thu thập telemetry",
                    List.of(config("app.alloy.url")), List.of(),
                    List.of("Thu thập và chuyển tiếp các dữ liệu telemetry đến hệ thống giám sát."));
            default -> throw new ResponseStatusException(NOT_FOUND, "Unsupported dependency: " + name);
        };
    }

    public hcmute.edu.zentech.dto.response.AdminPingResponse pingDependency(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        long start = System.nanoTime();
        boolean up;
        try {
            up = switch (normalized) {
                case "mysql" -> checkMysql();
                case "rabbitmq", "rabbit" -> checkRabbit();
                case "qdrant" -> checkHttpReady(qdrantUrl + "/readyz");
                case "zentech ai", "zentech-ai" -> checkHttpReady(aiBaseUrl + "/health");
                case "zentech fe", "zentech-fe", "fe" -> true;
                case "zentech java be", "zentech-be", "zentech be", "be" -> true;
                case "prometheus" -> prometheus.isReady();
                case "loki" -> checkHttpReady(lokiUrl + "/ready");
                case "alloy" -> checkHttpReady(alloyUrl + "/-/ready");
                default -> throw new ResponseStatusException(NOT_FOUND, "Unsupported dependency: " + name);
            };
        } catch (Exception e) {
            up = false;
        }
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
        latencyMs = Math.round(latencyMs * 10.0) / 10.0;
        return hcmute.edu.zentech.dto.response.AdminPingResponse.builder()
                .status(up ? "UP" : "DOWN")
                .latencyMs(latencyMs)
                .build();
    }

    private AdminDependencyDetailResponse mysqlDetail() {
        boolean up = checkMysql();
        return AdminDependencyDetailResponse.builder()
                .name("MySQL")
                .group("Business service")
                .status(up ? "UP" : "DOWN")
                .detail(up ? "Nhóm kết nối cơ sở dữ liệu (Connection pool) đã sẵn sàng" : "Kết nối cơ sở dữ liệu thất bại")
                .endpoint(value("spring.datasource.url"))
                .healthCheckPath("Kiểm tra kết nối JDBC")
                .lastCheckedAt(Instant.now())
                .configItems(List.of(
                        config("spring.datasource.url"),
                        config("spring.datasource.username"),
                        config("spring.datasource.password"),
                        config("spring.datasource.driver-class-name")
                ))
                .metrics(List.of(
                        metric("Kết nối đang hoạt động", prometheus.queryScalar("hikaricp_connections_active"), "hoạt động"),
                        metric("Kết nối nhàn rỗi", prometheus.queryScalar("hikaricp_connections_idle"), "nhàn rỗi"),
                        metric("Kết nối đang chờ", prometheus.queryScalar("hikaricp_connections_pending"), "đang chờ")
                ))
                .notes(List.of("Thông tin cấu hình và tài khoản kết nối cơ sở dữ liệu MySQL."))
                .build();
    }

    private AdminDependencyDetailResponse rabbitDetail() {
        boolean up = checkRabbit();
        return AdminDependencyDetailResponse.builder()
                .name("RabbitMQ")
                .group("Business service")
                .status(up ? "UP" : "DOWN")
                .detail(up ? "Kết nối đến Message Broker đang hoạt động" : "Kết nối đến Message Broker thất bại")
                .endpoint(value("spring.rabbitmq.host") + ":" + value("spring.rabbitmq.port"))
                .healthCheckPath("Kiểm tra kết nối AMQP")
                .lastCheckedAt(Instant.now())
                .configItems(List.of(
                        config("spring.rabbitmq.host"),
                        config("spring.rabbitmq.port"),
                        config("spring.rabbitmq.username"),
                        config("spring.rabbitmq.password"),
                        config("app.websocket.relay.host"),
                        config("app.websocket.relay.port"),
                        config("app.websocket.relay.username"),
                        config("app.websocket.relay.password")
                ))
                .metrics(List.of(
                        metric("Kết nối Broker", prometheus.queryScalar("rabbitmq_connections"), "kết nối"),
                        metric("Kênh Broker (Channels)", prometheus.queryScalar("rabbitmq_channels"), "kênh")
                ))
                .notes(List.of("RabbitMQ được sử dụng cho hệ thống tin nhắn bất đồng bộ và chuyển tiếp WebSocket STOMP."))
                .build();
    }

    private AdminDependencyDetailResponse prometheusDetail() {
        String url = value("app.prometheus.url");
        boolean up = prometheus.isReady();
        return httpDependencyDetail("Prometheus", "Observability infrastructure", url, "/-/ready", up,
                "Kiểm tra độ sẵn sàng của API truy vấn metric",
                List.of(
                        config("app.prometheus.url"),
                        config("app.prometheus.scrape-token"),
                        config("app.prometheus.query-timeout-ms"),
                        config("management.prometheus.metrics.export.enabled"),
                        config("management.endpoints.web.exposure.include")
                ),
                List.of(
                        metric("CPU Máy chủ", prometheus.queryScalar("zentech_host_cpu_usage_percent"), "%"),
                        metric("RAM Máy chủ", prometheus.queryScalar("zentech_host_ram_usage_percent"), "%"),
                        metric("Ổ đĩa Máy chủ", prometheus.queryScalar("zentech_host_disk_usage_percent"), "%")
                ),
                List.of("Prometheus là nguồn dữ liệu duy nhất cung cấp chỉ số cho trang giám sát tài nguyên hệ thống."));
    }

    private AdminDependencyDetailResponse httpDependencyDetail(String name, String group, String endpoint, String readyPath,
            boolean up, String healthCheckPath, List<AdminDependencyDetailResponse.ConfigItem> configItems,
            List<AdminDependencyDetailResponse.MetricItem> metrics, List<String> notes) {
        return AdminDependencyDetailResponse.builder()
                .name(name)
                .group(group)
                .status(up ? "UP" : "DOWN")
                .detail(up ? "Kiểm tra mức độ sẵn sàng của dịch vụ thành công" : "Kiểm tra mức độ sẵn sàng của dịch vụ thất bại")
                .endpoint(endpoint)
                .healthCheckPath(readyPath == null ? healthCheckPath : endpoint + readyPath)
                .lastCheckedAt(Instant.now())
                .configItems(configItems)
                .metrics(metrics)
                .notes(notes)
                .build();
    }

    private AdminDependencyDetailResponse.ConfigItem configItem(String key, String value) {
        return AdminDependencyDetailResponse.ConfigItem.builder()
                .key(key)
                .value(value)
                .source("Môi trường Spring")
                .sensitive(false)
                .editable(false)
                .build();
    }

    private AdminDependencyDetailResponse.ConfigItem config(String key) {
        boolean sensitive = isSensitiveKey(key);
        String rawValue = value(key);
        return AdminDependencyDetailResponse.ConfigItem.builder()
                .key(key)
                .value(sensitive ? mask(rawValue) : rawValue)
                .source("Môi trường Spring")
                .sensitive(sensitive)
                .editable(false)
                .build();
    }

    private AdminDependencyDetailResponse.MetricItem metric(String label, Double value, String unit) {
        return AdminDependencyDetailResponse.MetricItem.builder()
                .label(label)
                .value(value == null ? "N/A" : String.format(Locale.US, "%.1f", value))
                .unit(unit)
                .build();
    }

    private String value(String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? "Chưa cấu hình" : value;
    }

    private boolean isSensitiveKey(String key) {
        return false;
    }

    private String mask(String value) {
        if (value == null || value.isBlank() || "Chưa cấu hình".equals(value)) return "Chưa cấu hình";
        return "********";
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

    private List<AdminObservabilityResponse.DependencyStatus> checkDependencies(boolean prometheusAvailable) {
        List<AdminObservabilityResponse.DependencyStatus> result = new ArrayList<>();
        result.add(dependency("Prometheus", prometheusAvailable, prometheusAvailable ? "Đang thu thập dữ liệu metric" : "Không thể kết nối Prometheus", null, null, null, null));

        boolean mysqlUp = checkMysql();
        result.add(dependency("MySQL", mysqlUp, mysqlUp ? "Pool kết nối sẵn sàng" : "Kết nối database thất bại",
                prometheus.queryScalar("hikaricp_connections_active"), "hoạt động",
                prometheus.queryScalar("hikaricp_connections_idle"), "nhàn rỗi"));

        boolean rabbitUp = checkRabbit();
        result.add(dependency("RabbitMQ", rabbitUp, rabbitUp ? "Broker connection hoạt động" : "Không thể kết nối broker",
                prometheus.queryScalar("rabbitmq_connections"), "kết nối",
                prometheus.queryScalar("rabbitmq_channels"), "kênh"));

        boolean lokiUp = checkHttpReady(lokiUrl + "/ready");
        result.add(dependency("Loki", lokiUp, lokiUp ? "Kho lưu trữ nhật ký (Log storage) sẵn sàng" : "Loki không phản hồi", null, null, null, null));

        boolean qdrantUp = checkHttpReady(qdrantUrl + "/readyz");
        result.add(dependency("Qdrant", qdrantUp, qdrantUp ? "Cơ sở dữ liệu Vector sẵn sàng" : "Qdrant không phản hồi", null, null, null, null));

        boolean aiUp = checkHttpReady(aiBaseUrl + "/health");
        result.add(dependency("ZenTech AI", aiUp, aiUp ? "Dịch vụ AI sẵn sàng" : "ZenTech AI không phản hồi", null, null, null, null));

        boolean alloyUp = checkHttpReady(alloyUrl + "/-/ready");
        result.add(dependency("Alloy", alloyUp, alloyUp ? "Bộ thu thập telemetry hoạt động" : "Ngừng thu thập telemetry", null, null, null, null));
        return result;
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

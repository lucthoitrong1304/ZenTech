package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.response.AdminResourceMetricsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PrometheusQueryService {
    private static final String CPU_METRIC = "zentech_host_cpu_usage_percent";
    private static final String RAM_METRIC = "zentech_host_ram_usage_percent";
    private static final String DISK_METRIC = "zentech_host_disk_usage_percent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String prometheusUrl;

    public PrometheusQueryService(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${app.prometheus.url:http://localhost:9090}") String prometheusUrl,
            @Value("${app.prometheus.query-timeout-ms:3000}") long timeoutMs
    ) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.objectMapper = objectMapper;
        this.prometheusUrl = prometheusUrl;
    }

    public HistoryResult queryHostHistory(Instant from, Instant to, long stepSeconds) {
        try {
            Map<Long, MutablePoint> points = new LinkedHashMap<>();
            mergeHostSeries(points, queryRange(CPU_METRIC, from, to, stepSeconds), Metric.CPU);
            mergeHostSeries(points, queryRange(RAM_METRIC, from, to, stepSeconds), Metric.RAM);
            mergeHostSeries(points, queryRange(DISK_METRIC, from, to, stepSeconds), Metric.DISK);
            return new HistoryResult(true, points.values().stream().map(MutablePoint::toResponse).toList());
        } catch (Exception exception) {
            log.debug("Prometheus history is unavailable: {}", exception.getMessage());
            return new HistoryResult(false, List.of());
        }
    }

    public boolean isReady() {
        try {
            return restTemplate.getForEntity(prometheusUrl + "/-/ready", String.class).getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            return false;
        }
    }

    public Double queryScalar(String query) {
        try {
            List<LabeledValue> values = queryVector(query);
            return values.isEmpty() ? null : values.getFirst().value();
        } catch (Exception exception) {
            log.debug("Prometheus scalar query failed [{}]: {}", query, exception.getMessage());
            return null;
        }
    }

    public List<LabeledValue> queryVectorSafe(String query) {
        try {
            return queryVector(query);
        } catch (Exception exception) {
            log.debug("Prometheus vector query failed [{}]: {}", query, exception.getMessage());
            return List.of();
        }
    }

    public List<Sample> queryRangeSafe(String query, Instant from, Instant to, long stepSeconds) {
        try {
            return queryRange(query, from, to, stepSeconds);
        } catch (Exception exception) {
            log.debug("Prometheus range query failed [{}]: {}", query, exception.getMessage());
            return List.of();
        }
    }

    private List<LabeledValue> queryVector(String query) throws Exception {
        String response = restTemplate.getForObject(
                prometheusUrl + "/api/v1/query?query={query}",
                String.class,
                Map.of("query", query)
        );
        JsonNode root = readSuccessResponse(response);
        JsonNode result = root.path("data").path("result");
        List<LabeledValue> values = new ArrayList<>();
        for (JsonNode item : result) {
            JsonNode rawValue = item.path("value");
            if (!rawValue.isArray() || rawValue.size() < 2) continue;
            Double value = parseValue(rawValue.get(1).asText());
            if (value == null) continue;
            Map<String, String> labels = new LinkedHashMap<>();
            item.path("metric").fields().forEachRemaining(entry -> labels.put(entry.getKey(), entry.getValue().asText()));
            values.add(new LabeledValue(labels, value));
        }
        return values;
    }

    private List<Sample> queryRange(String query, Instant from, Instant to, long stepSeconds) throws Exception {
        String response = restTemplate.getForObject(
                prometheusUrl + "/api/v1/query_range?query={query}&start={start}&end={end}&step={step}",
                String.class,
                Map.of(
                        "query", query,
                        "start", from.getEpochSecond(),
                        "end", to.getEpochSecond(),
                        "step", stepSeconds
                )
        );
        JsonNode root = readSuccessResponse(response);
        JsonNode result = root.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) return List.of();
        JsonNode values = result.get(0).path("values");
        List<Sample> samples = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isArray() || value.size() < 2) continue;
            Double parsed = parseValue(value.get(1).asText());
            if (parsed != null) samples.add(new Sample(Math.round(value.get(0).asDouble()), parsed));
        }
        return samples;
    }

    private JsonNode readSuccessResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        if (!"success".equals(root.path("status").asText())) throw new IllegalStateException("Prometheus query failed");
        return root;
    }

    private Double parseValue(String rawValue) {
        try {
            double value = Double.parseDouble(rawValue);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void mergeHostSeries(Map<Long, MutablePoint> points, List<Sample> samples, Metric metric) {
        for (Sample sample : samples) {
            MutablePoint point = points.computeIfAbsent(sample.timestamp(), MutablePoint::new);
            switch (metric) {
                case CPU -> point.cpu = sample.value();
                case RAM -> point.ram = sample.value();
                case DISK -> point.disk = sample.value();
            }
        }
    }

    public record HistoryResult(boolean available, List<AdminResourceMetricsResponse.ResourceHistoryPoint> history) {}
    public record Sample(long timestamp, double value) {}
    public record LabeledValue(Map<String, String> labels, double value) {}
    private enum Metric { CPU, RAM, DISK }

    private static class MutablePoint {
        private final long timestamp;
        private Double cpu;
        private Double ram;
        private Double disk;
        private MutablePoint(long timestamp) { this.timestamp = timestamp; }
        private AdminResourceMetricsResponse.ResourceHistoryPoint toResponse() {
            return AdminResourceMetricsResponse.ResourceHistoryPoint.builder()
                    .timestamp(Instant.ofEpochSecond(timestamp))
                    .cpuUsagePercent(cpu).ramUsagePercent(ram).diskUsagePercent(disk).build();
        }
    }
}
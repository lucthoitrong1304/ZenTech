package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminObservabilityResponse {
    private String period;
    private Instant from;
    private Instant to;
    private Instant generatedAt;
    private boolean prometheusAvailable;
    private HealthOverview health;
    private ApiPerformance api;
    private List<MetricHistoryPoint> history;
    private List<ApiAnomaly> slowApis;
    private List<ApiAnomaly> errorApis;
    private List<ThresholdEvent> thresholdEvents;
    private List<DependencyStatus> dependencies;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HealthOverview {
        private Double cpuUsagePercent;
        private Long cpuCoreCount;
        private Double ramUsagePercent;
        private Long ramUsedBytes;
        private Long ramTotalBytes;
        private Double diskUsagePercent;
        private Long diskUsedBytes;
        private Long diskTotalBytes;
        private Double jvmHeapUsagePercent;
        private Long jvmHeapUsedBytes;
        private Long jvmHeapMaxBytes;
        private Double processCpuUsagePercent;
        private Long processUptimeSeconds;
        private Double liveThreads;
        private Double peakThreads;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiPerformance {
        private Double requestsPerMinute;
        private Double errorRatePercent;
        private Double p95LatencyMs;
        private Double averageLatencyMs;
        private Double activeRequests;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MetricHistoryPoint {
        private Instant timestamp;
        private Double cpuUsagePercent;
        private Double ramUsagePercent;
        private Double diskUsagePercent;
        private Double jvmHeapUsagePercent;
        private Double requestsPerMinute;
        private Double errorRatePercent;
        private Double p95LatencyMs;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiAnomaly {
        private String method;
        private String uri;
        private String status;
        private Double value;
        private String unit;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ThresholdEvent {
        private Instant timestamp;
        private String metric;
        private Double value;
        private Double threshold;
        private String severity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DependencyStatus {
        private String name;
        private String status;
        private String detail;
        private Double primaryValue;
        private String primaryUnit;
        private Double secondaryValue;
        private String secondaryUnit;
    }
}
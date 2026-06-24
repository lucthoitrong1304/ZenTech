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
public class AdminResourceMetricsResponse {
    private String status;
    private String source;
    private boolean historyAvailable;
    private Double cpuUsagePercent;
    private Double ramUsagePercent;
    private Double diskUsagePercent;
    private Long ramUsedBytes;
    private Long ramTotalBytes;
    private Long diskUsedBytes;
    private Long diskTotalBytes;
    private String diskPath;
    private Instant generatedAt;
    private String message;
    private List<ResourceHistoryPoint> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceHistoryPoint {
        private Instant timestamp;
        private Double cpuUsagePercent;
        private Double ramUsagePercent;
        private Double diskUsagePercent;
    }
}

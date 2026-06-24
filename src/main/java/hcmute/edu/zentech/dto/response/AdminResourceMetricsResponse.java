package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminResourceMetricsResponse {
    private String status;
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
}

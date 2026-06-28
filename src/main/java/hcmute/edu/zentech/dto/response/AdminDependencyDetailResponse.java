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
public class AdminDependencyDetailResponse {
    private String name;
    private String group;
    private String status;
    private String detail;
    private String endpoint;
    private String healthCheckPath;
    private Instant lastCheckedAt;
    private List<ConfigItem> configItems;
    private List<MetricItem> metrics;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigItem {
        private String key;
        private String value;
        private String source;
        private boolean sensitive;
        private boolean editable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricItem {
        private String label;
        private String value;
        private String unit;
    }
}

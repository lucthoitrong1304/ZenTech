package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatisticsResponse {
    private String period;
    private Instant from;
    private Instant to;
    private Instant generatedAt;
    private boolean logsAvailable;
    private boolean partialData;
    private long totalErrors;
    private long incidentsInPeriod;
    private long ticketsCreated;
    private long ticketsResolved;
    private double ticketResolutionRate;
    private List<ErrorTrendPoint> errorTrend;
    private List<ApiErrorItem> topApis;
    private List<ServiceErrorItem> topServices;
    private List<AffectedUserItem> topAffectedUsers;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorTrendPoint {
        private String key;
        private String label;
        private long total;
        private long warnings;
        private long errors;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiErrorItem {
        private String method;
        private String endpoint;
        private Integer statusCode;
        private long errorCount;
        private Instant lastSeen;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ServiceErrorItem {
        private String service;
        private long total;
        private long warnings;
        private long errors;
        private Instant lastSeen;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AffectedUserItem {
        private UUID userId;
        private String displayName;
        private String email;
        private Role role;
        private String avatarUrl;
        private long errorCount;
        private Instant lastSeen;
        private boolean anonymous;
    }
}

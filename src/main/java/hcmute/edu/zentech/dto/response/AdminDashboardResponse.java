package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.model.TicketPriority;
import hcmute.edu.zentech.model.TicketStatus;
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
public class AdminDashboardResponse {
    private String period;
    private Instant from;
    private Instant to;
    private String health;
    private Instant generatedAt;
    private boolean logsAvailable;
    private Metrics metrics;
    private List<TrendPoint> trend;
    private List<IssueItem> topIssues;
    private List<IncidentItem> priorityIncidents;
    private List<TicketItem> priorityTickets;
    private List<ServiceErrorItem> topServices;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Metrics {
        private long issuesInPeriod;
        private long errorsInPeriod;
        private long openIncidents;
        private long highPriorityIncidents;
        private long unassignedIncidents;
        private long actionableTickets;
        private long unassignedTickets;
        private long staleTickets;
        private long incidentsCreatedInPeriod;
        private long incidentsResolvedInPeriod;
        private double incidentResolutionRate;
        private long averageResolutionMinutes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrendPoint {
        private String key;
        private String label;
        private long issues;
        private long errors;
        private long incidentsCreated;
        private long incidentsResolved;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IssueItem {
        private String signature;
        private String title;
        private String level;
        private String category;
        private long occurrences;
        private Instant firstSeen;
        private Instant lastSeen;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IncidentItem {
        private UUID id;
        private String code;
        private String title;
        private IncidentSeverity severity;
        private IncidentStatus status;
        private String serviceName;
        private String apiPath;
        private String assignee;
        private Instant createdAt;
        private Instant firstOccurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TicketItem {
        private UUID id;
        private String code;
        private String title;
        private TicketPriority priority;
        private TicketStatus status;
        private String assigneeName;
        private String assigneeEmail;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ServiceErrorItem {
        private String service;
        private long occurrences;
        private String latestIssueTitle;
        private Instant lastSeen;
    }
}

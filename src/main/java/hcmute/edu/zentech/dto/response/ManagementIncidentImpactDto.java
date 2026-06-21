package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementIncidentImpactDto {
    private UUID incidentId;
    private String incidentCode;
    private String serviceName;
    private String apiPath;
    private String httpMethod;
    private Integer statusCode;
    private Instant occurredAt;
    private Instant firstOccurredAt; // Thời điểm sự cố xảy ra lần đầu tiên
    private Instant resolvedAt;
    private IncidentStatus status;
    private Long durationMinutes;
    
    private Double actualRevenue;
    private Double expectedRevenue;
    private Double revenueLoss;
    
    private Integer actualOrders;
    private Integer expectedOrders;
    private Integer lostOrders;
    
    private Integer affectedUsers;
    private IncidentSeverity severity;
    private String aiSummary;
}

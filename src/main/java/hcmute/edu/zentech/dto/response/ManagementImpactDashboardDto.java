package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementImpactDashboardDto {
    private Double totalLostRevenue;
    private Integer totalLostOrders;
    private Integer totalAffectedUsers;
    private Long totalIncidentsCount;
    private Long criticalIncidentsCount;
    private Long highIncidentsCount;
    private Long mediumIncidentsCount;
    private Long lowIncidentsCount;
}

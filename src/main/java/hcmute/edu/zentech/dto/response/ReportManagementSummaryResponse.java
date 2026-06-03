package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportManagementSummaryResponse {
    private double totalRevenue;
    private double forecastedRevenue;
    private double growthRate;
    private long totalOrders;
    private double averageOrderValue; // AOV
    private double aiOpsScore;
    private double autoFulfillmentRate;
}

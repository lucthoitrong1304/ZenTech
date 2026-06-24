package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportManagementInventoryStatsResponse {
    private double totalInventoryValue;
    private int totalItemsInStock;
    private int lowStockVariations;
    private int deadStockVariations;
    private double totalFaultyValue;
    private int totalFaultyItems;
    private List<ReportManagementProductPerformanceResponse> lowStockProducts;
}

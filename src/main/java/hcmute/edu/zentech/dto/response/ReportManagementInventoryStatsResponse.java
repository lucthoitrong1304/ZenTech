package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportManagementInventoryStatsResponse {
    private double totalInventoryValue;
    private int totalItemsInStock;
    private int lowStockVariations;
    private int deadStockVariations;
}

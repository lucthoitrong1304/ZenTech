package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStatsResponse {
    private long totalItems;
    private long lowStockCount;
    private long outOfStockCount;
    private long totalFaultyVariants;
    private long totalFaultyQuantity;
    private long highFaultyAlertCount;
}

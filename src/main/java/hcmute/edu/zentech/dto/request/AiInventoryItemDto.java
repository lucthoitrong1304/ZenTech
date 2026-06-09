package hcmute.edu.zentech.dto.request;

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
public class AiInventoryItemDto {
    private String productName;
    private String variantName;
    private int currentStock;
    private double averageWeeklySales;
    private int suggestedQty;
}

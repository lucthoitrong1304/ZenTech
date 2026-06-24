package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySummaryResponse {
    private UUID variantId;
    private UUID productId;
    private String productName;
    private String variantName;
    private String colorCode;
    private double originalPrice;
    private Double salePrice;
    private int stockQuantity;
    private int faultyQuantity;
    private String representativeImageUrl;
}

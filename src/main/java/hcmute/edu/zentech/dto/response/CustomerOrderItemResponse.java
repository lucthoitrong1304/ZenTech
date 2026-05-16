package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderItemResponse {
    private UUID orderItemId;
    private UUID productVariantId;
    private String productName;
    private String variantName;
    private int quantity;
    private double unitPrice;
    private double priceAtPurchase;
    private double lineTotal;
    private double subtotal;
    private String productImage;
}

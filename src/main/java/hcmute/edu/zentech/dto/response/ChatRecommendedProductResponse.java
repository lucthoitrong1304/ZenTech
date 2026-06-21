package hcmute.edu.zentech.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRecommendedProductResponse {
    private UUID productId;
    private UUID variantId;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Integer stock;
    private String productUrl;
}

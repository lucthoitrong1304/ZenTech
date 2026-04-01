package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDetailResponse {
    private UUID id;
    private double originalPrice;
    private Double salePrice;
    private String name;
    private String nameColor;
    private String colorCode;
    private Instant saleStartAt;
    private Instant saleEndAt;
    private int stockQuantity;
    private List<String> imageUrls;
}

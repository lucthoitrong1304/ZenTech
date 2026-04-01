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
public class ProductDetailResponse {
    private UUID id;
    private String productName;
    private String specifications;
    private String compatibility;
    private String boxContents;
    private String supportInfo;
    private Instant createdAt;
    private List<String> productImageUrls;
    private List<CategoryProductListItemResponse> similarProducts;
    private List<ProductVariantDetailResponse> variants;
    private Double averageRating;
    private long totalReviews;
}

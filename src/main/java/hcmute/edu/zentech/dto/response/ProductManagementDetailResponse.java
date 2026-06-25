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
public class ProductManagementDetailResponse {
    private UUID id;
    private String productName;
    private String specifications;
    private String compatibility;
    private String boxContents;
    private String supportInfo;
    private String representativeImageKey;
    private String representativeImageUrl;
    private List<String> imageKeys;
    private List<String> productImageUrls;
    private ProductGroupResponse productGroup;
    private List<ProductCategorySummaryResponse> categories;
    private List<ProductVariantManagementResponse> variants;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}

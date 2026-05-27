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
public class ProductManagementSummaryResponse {
    private UUID id;
    private String productName;
    private String representativeImageUrl;
    private UUID productGroupId;
    private String productGroupName;
    private List<ProductCategorySummaryResponse> categories;
    private int variantCount;
    private double price;
    private int stock;
    private String status;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}

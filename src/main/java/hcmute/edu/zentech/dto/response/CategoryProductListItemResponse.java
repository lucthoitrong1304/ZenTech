package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryProductListItemResponse {
    private UUID id;
    private String productName;
    private List<String> imageUrls;
    private Double originalPrice;
    private Double salePrice;
    private Double averageRating;
}

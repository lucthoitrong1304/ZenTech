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
public class ProductCategorySummaryResponse {
    private UUID id;
    private String categoryName;
    private String shortName;
    private boolean hasChildren;
    private List<ProductCategorySummaryResponse> children;
}

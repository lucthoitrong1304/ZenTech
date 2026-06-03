package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportManagementProductPerformanceResponse {
    private String productName;
    private String variantName;
    private String imageUrl;
    private String categoryName;
    private double price;
    private int quantitySold;
    private double revenue;
    private int stockRemaining;
}

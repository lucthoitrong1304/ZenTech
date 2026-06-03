package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportManagementCouponPerformanceResponse {
    private String couponCode;
    private long usageCount;
    private double totalDiscountApplied;
}

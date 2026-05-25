package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.CouponType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderCouponResponse {
    private UUID orderCouponId;
    private String couponCode;
    private CouponType couponType;
    private Double discountValue;
    private Double maxDiscount;
    private Double appliedAmount;
}

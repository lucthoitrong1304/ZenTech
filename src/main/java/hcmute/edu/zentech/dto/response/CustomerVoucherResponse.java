package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.CouponType;
import hcmute.edu.zentech.model.CustomerVoucherStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerVoucherResponse {
    private UUID voucherId;
    private UUID couponId;
    private String couponCode;
    private CouponType couponType;
    private double discountValue;
    private double maxDiscount;
    private double minOrderAmount;
    private Instant startAt;
    private Instant endAt;
    private CustomerVoucherStatus status;
    private Instant issuedAt;
    private Instant usedAt;
}

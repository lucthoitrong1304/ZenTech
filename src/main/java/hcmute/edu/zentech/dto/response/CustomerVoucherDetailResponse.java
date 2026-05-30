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
public class CustomerVoucherDetailResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private UUID couponId;
    private String couponCode;
    private CouponType couponType;
    private double discountValue;
    private Instant issuedAt;
    private Instant usedAt;
    private CustomerVoucherStatus status;
}

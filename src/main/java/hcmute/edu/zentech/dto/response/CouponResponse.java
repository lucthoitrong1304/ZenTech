package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.CouponType;
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
public class CouponResponse {
    private UUID id;
    private String code;
    private CouponType type;
    private double discountValue;
    private double maxDiscount;
    private double minOrderAmount;
    private Instant startAt;
    private Instant endAt;
    private int usageLimit;
    private int usedCount;
    private boolean active;
}

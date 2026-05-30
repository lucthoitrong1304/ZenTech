package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class CouponRequest {
    @NotBlank(message = "code is required")
    @Size(max = 50, message = "code must not exceed 50 characters")
    private String code;

    @NotNull(message = "type is required")
    private CouponType type;

    private double discountValue;

    private double maxDiscount;

    private double minOrderAmount;

    private Instant startAt;

    private Instant endAt;

    private int usageLimit;

    private boolean active = true;
}

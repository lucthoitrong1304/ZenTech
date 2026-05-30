package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.request.CouponRequest;
import hcmute.edu.zentech.dto.response.CouponResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherDetailResponse;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.CustomerVoucherStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CouponManagementMapper {

    public CouponResponse toResponse(Coupon coupon) {
        if (coupon == null) {
            return null;
        }
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .type(coupon.getType())
                .discountValue(coupon.getDiscountValue())
                .maxDiscount(coupon.getMaxDiscount())
                .minOrderAmount(coupon.getMinOrderAmount())
                .startAt(coupon.getStartAt())
                .endAt(coupon.getEndAt())
                .usageLimit(coupon.getUsageLimit())
                .usedCount(coupon.getUsedCount())
                .active(coupon.isActive())
                .build();
    }

    public Coupon toEntity(CouponRequest request) {
        if (request == null) {
            return null;
        }
        Coupon coupon = new Coupon();
        coupon.setCode(request.getCode().trim().toUpperCase());
        coupon.setType(request.getType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMaxDiscount(request.getMaxDiscount());
        coupon.setMinOrderAmount(request.getMinOrderAmount());
        coupon.setStartAt(request.getStartAt());
        coupon.setEndAt(request.getEndAt());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setActive(request.isActive());
        coupon.setUsedCount(0);
        return coupon;
    }

    public void updateEntity(CouponRequest request, Coupon coupon) {
        if (request == null || coupon == null) {
            return;
        }
        coupon.setCode(request.getCode().trim().toUpperCase());
        coupon.setType(request.getType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMaxDiscount(request.getMaxDiscount());
        coupon.setMinOrderAmount(request.getMinOrderAmount());
        coupon.setStartAt(request.getStartAt());
        coupon.setEndAt(request.getEndAt());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setActive(request.isActive());
    }

    public CustomerVoucherDetailResponse toCustomerVoucherResponse(CustomerVoucher cv) {
        if (cv == null) {
            return null;
        }

        CustomerVoucherStatus status = CustomerVoucherStatus.AVAILABLE;
        if (cv.getUsedAt() != null) {
            status = CustomerVoucherStatus.USED;
        } else if (cv.getCoupon() != null && cv.getCoupon().getEndAt() != null && cv.getCoupon().getEndAt().isBefore(Instant.now())) {
            status = CustomerVoucherStatus.EXPIRED;
        }

        String customerName = cv.getCustomer() != null ? cv.getCustomer().getFullName() : "";
        String customerEmail = cv.getCustomer() != null && cv.getCustomer().getUserInfo() != null 
                ? cv.getCustomer().getUserInfo().getEmail() : "";

        return CustomerVoucherDetailResponse.builder()
                .id(cv.getId())
                .customerId(cv.getCustomer() != null ? cv.getCustomer().getId() : null)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .couponId(cv.getCoupon() != null ? cv.getCoupon().getId() : null)
                .couponCode(cv.getCoupon() != null ? cv.getCoupon().getCode() : "")
                .couponType(cv.getCoupon() != null ? cv.getCoupon().getType() : null)
                .discountValue(cv.getCoupon() != null ? cv.getCoupon().getDiscountValue() : 0.0)
                .issuedAt(cv.getIssuedAt())
                .usedAt(cv.getUsedAt())
                .status(status)
                .build();
    }
}

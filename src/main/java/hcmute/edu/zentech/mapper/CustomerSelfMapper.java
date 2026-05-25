package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.CustomerAddressResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderCouponResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderItemResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherResponse;
import hcmute.edu.zentech.dto.response.MyProfileResponse;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.CustomerVoucherStatus;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderCoupon;
import hcmute.edu.zentech.model.OrderDetail;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CustomerSelfMapper {

    public MyProfileResponse toMyProfileResponse(Customer customer) {
        return MyProfileResponse.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getUserInfo().getEmail())
                .imageUrl(customer.getImageUrl())
                .registeredAt(customer.getUserInfo().getCreatedAt())
                .build();
    }

    public CustomerAddressResponse toCustomerAddressResponse(Address address) {
        if (address == null) {
            return null;
        }

        return CustomerAddressResponse.builder()
                .addressId(address.getId())
                .phoneNumber(address.getPhoneNumber())
                .province(address.getProvince())
                .ward(address.getWard())
                .street(address.getStreet())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    public CustomerOrderHistoryResponse toCustomerOrderHistoryResponse(
            Order order,
            Collection<OrderDetail> orderDetails,
            Map<UUID, String> productImageUrls
    ) {
        List<CustomerOrderItemResponse> items = orderDetails.stream()
                .map(orderDetail -> toCustomerOrderItemResponse(orderDetail, productImageUrls.get(orderDetail.getId())))
                .toList();

        return CustomerOrderHistoryResponse.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .finalPrice(order.getFinalPrice())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .items(items)
                .build();
    }

    public CustomerOrderDetailResponse toCustomerOrderDetailResponse(
            Order order,
            Collection<OrderDetail> orderDetails,
            Map<UUID, String> productImageUrls
    ) {
        List<CustomerOrderItemResponse> items = orderDetails.stream()
                .map(orderDetail -> toCustomerOrderItemResponse(orderDetail, productImageUrls.get(orderDetail.getId())))
                .toList();

        List<CustomerOrderCouponResponse> coupons = order.getOrderCoupons() == null
                ? List.of()
                : order.getOrderCoupons().stream()
                .sorted(Comparator.comparing(OrderCoupon::getCouponCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toCustomerOrderCouponResponse)
                .toList();

        return CustomerOrderDetailResponse.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .originalTotalPrice(order.getOriginalTotalPrice())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .finalPrice(order.getFinalPrice())
                .shippingAddress(toCustomerAddressResponse(order.getAddress()))
                .items(items)
                .coupons(coupons)
                .build();
    }

    public CustomerVoucherResponse toCustomerVoucherResponse(CustomerVoucher voucher, CustomerVoucherStatus status) {
        Coupon coupon = voucher.getCoupon();

        return CustomerVoucherResponse.builder()
                .voucherId(voucher.getId())
                .couponId(coupon.getId())
                .couponCode(coupon.getCode())
                .couponType(coupon.getType())
                .discountValue(coupon.getDiscountValue())
                .maxDiscount(coupon.getMaxDiscount())
                .minOrderAmount(coupon.getMinOrderAmount())
                .startAt(coupon.getStartAt())
                .endAt(coupon.getEndAt())
                .status(status)
                .issuedAt(voucher.getIssuedAt())
                .usedAt(voucher.getUsedAt())
                .build();
    }

    private CustomerOrderCouponResponse toCustomerOrderCouponResponse(OrderCoupon orderCoupon) {
        return CustomerOrderCouponResponse.builder()
                .orderCouponId(orderCoupon.getId())
                .couponCode(orderCoupon.getCouponCode())
                .couponType(orderCoupon.getCouponType())
                .discountValue(orderCoupon.getDiscountValue())
                .maxDiscount(orderCoupon.getMaxDiscount())
                .appliedAmount(orderCoupon.getAppliedAmount())
                .build();
    }

    private CustomerOrderItemResponse toCustomerOrderItemResponse(OrderDetail orderDetail, String productImage) {
        String productName = orderDetail.getProductVariant() != null && orderDetail.getProductVariant().getProduct() != null
                ? orderDetail.getProductVariant().getProduct().getProductName()
                : null;

        String variantName = orderDetail.getProductVariant() != null
                ? orderDetail.getProductVariant().getName()
                : null;

        return CustomerOrderItemResponse.builder()
                .orderItemId(orderDetail.getId())
                .productVariantId(orderDetail.getProductVariant() != null ? orderDetail.getProductVariant().getId() : null)
                .productName(productName)
                .variantName(variantName)
                .quantity(orderDetail.getQuantity())
                .unitPrice(orderDetail.getPriceAtPurchase())
                .priceAtPurchase(orderDetail.getPriceAtPurchase())
                .lineTotal(orderDetail.getPriceAtPurchase() * orderDetail.getQuantity())
                .subtotal(orderDetail.getPriceAtPurchase() * orderDetail.getQuantity())
                .productImage(productImage)
                .build();
    }
}

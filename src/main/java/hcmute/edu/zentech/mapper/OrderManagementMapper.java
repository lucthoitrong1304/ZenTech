package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.CustomerAddressResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderCouponResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderItemResponse;
import hcmute.edu.zentech.dto.response.OrderManagementCustomerResponse;
import hcmute.edu.zentech.dto.response.OrderManagementDetailResponse;
import hcmute.edu.zentech.dto.response.OrderManagementSummaryResponse;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderCoupon;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderManagementMapper {
    private final R2StorageService r2StorageService;

    public OrderManagementSummaryResponse toSummaryResponse(
            Order order,
            Collection<OrderDetail> orderDetails
    ) {
        return OrderManagementSummaryResponse.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .originalTotalPrice(order.getOriginalTotalPrice())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .finalPrice(order.getFinalPrice())
                .itemCount(orderDetails == null ? 0 : orderDetails.size())
                .customer(toCustomerResponse(order.getCustomer()))
                .build();
    }

    public OrderManagementDetailResponse toDetailResponse(
            Order order,
            Collection<OrderDetail> orderDetails,
            Map<UUID, String> productImageUrls
    ) {
        List<CustomerOrderItemResponse> items = orderDetails.stream()
                .map(orderDetail -> toOrderItemResponse(orderDetail, productImageUrls.get(orderDetail.getId())))
                .toList();

        List<CustomerOrderCouponResponse> coupons = order.getOrderCoupons() == null
                ? List.of()
                : order.getOrderCoupons().stream()
                .sorted(Comparator.comparing(OrderCoupon::getCouponCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toOrderCouponResponse)
                .toList();

        return OrderManagementDetailResponse.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .originalTotalPrice(order.getOriginalTotalPrice())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .finalPrice(order.getFinalPrice())
                .customer(toCustomerResponse(order.getCustomer()))
                .shippingAddress(toAddressResponse(order.getAddress()))
                .items(items)
                .coupons(coupons)
                .build();
    }

    private OrderManagementCustomerResponse toCustomerResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return OrderManagementCustomerResponse.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : null)
                .imageUrl(resolveImageUrl(customer.getImageUrl()))
                .build();
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }

    private CustomerAddressResponse toAddressResponse(Address address) {
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

    private CustomerOrderCouponResponse toOrderCouponResponse(OrderCoupon orderCoupon) {
        return CustomerOrderCouponResponse.builder()
                .orderCouponId(orderCoupon.getId())
                .couponCode(orderCoupon.getCouponCode())
                .couponType(orderCoupon.getCouponType())
                .discountValue(orderCoupon.getDiscountValue())
                .maxDiscount(orderCoupon.getMaxDiscount())
                .appliedAmount(orderCoupon.getAppliedAmount())
                .build();
    }

    private CustomerOrderItemResponse toOrderItemResponse(OrderDetail orderDetail, String productImage) {
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

package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.CustomerAddressResponse;
import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderItemResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.repository.projection.CustomerOrderAggregateProjection;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerManagementMapper {
    private final R2StorageService r2StorageService;

    public CustomerSummaryResponse toCustomerSummaryResponse(Customer customer, CustomerOrderAggregateProjection aggregate) {
        return CustomerSummaryResponse.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getUserInfo().getEmail())
                .active(customer.getUserInfo().isActive())
                .registeredAt(customer.getUserInfo().getCreatedAt())
                .totalOrders(getTotalOrders(aggregate))
                .totalSpent(getTotalSpent(aggregate))
                .lastOrderAt(getLastOrderAt(aggregate))
                .imageUrl(resolveImageUrl(customer.getImageUrl()))
                .build();
    }

    public CustomerDetailResponse toCustomerDetailResponse(Customer customer, CustomerOrderAggregateProjection aggregate) {
        List<CustomerAddressResponse> addresses = customer.getAddressList() == null
                ? List.of()
                : customer.getAddressList().stream()
                .filter(address -> !address.isDeleted())
                .map(this::toCustomerAddressResponse)
                .sorted(Comparator.comparing(CustomerAddressResponse::isDefault).reversed())
                .toList();

        return CustomerDetailResponse.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getUserInfo().getEmail())
                .active(customer.getUserInfo().isActive())
                .registeredAt(customer.getUserInfo().getCreatedAt())
                .addressList(addresses)
                .totalOrders(getTotalOrders(aggregate))
                .totalSpent(getTotalSpent(aggregate))
                .lastOrderAt(getLastOrderAt(aggregate))
                .imageUrl(resolveImageUrl(customer.getImageUrl()))
                .build();
    }

    public CustomerOrderHistoryResponse toCustomerOrderHistoryResponse(Order order, Collection<OrderDetail> orderDetails) {
        return toCustomerOrderHistoryResponse(order, orderDetails, Map.of());
    }

    public CustomerOrderHistoryResponse toCustomerOrderHistoryResponse(
            Order order,
            Collection<OrderDetail> orderDetails,
            Map<UUID, String> productImageUrls) {
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

    private CustomerAddressResponse toCustomerAddressResponse(Address address) {
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

    private long getTotalOrders(CustomerOrderAggregateProjection aggregate) {
        return aggregate != null ? aggregate.getTotalOrders() : 0L;
    }

    private double getTotalSpent(CustomerOrderAggregateProjection aggregate) {
        return aggregate != null ? aggregate.getTotalSpent() : 0D;
    }

    private Instant getLastOrderAt(CustomerOrderAggregateProjection aggregate) {
        return aggregate != null ? aggregate.getLastOrderAt() : null;
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }
}

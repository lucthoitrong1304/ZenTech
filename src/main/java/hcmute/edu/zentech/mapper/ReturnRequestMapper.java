package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.ReturnRequestResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderItemResponse;
import hcmute.edu.zentech.model.ReturnRequest;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReturnRequestMapper {
    private final R2StorageService r2StorageService;
    
    public ReturnRequestResponse toResponse(ReturnRequest request) {
        if (request == null) {
            return null;
        }

        UUID orderId = request.getOrder() != null ? request.getOrder().getId() : null;
        String customerName = request.getOrder() != null && request.getOrder().getCustomer() != null 
                ? request.getOrder().getCustomer().getFullName() : null;

        String customerEmail = null;
        String customerAvatarUrl = null;
        if (request.getOrder() != null && request.getOrder().getCustomer() != null) {
            Customer customer = request.getOrder().getCustomer();
            customerEmail = customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : null;
            String avatarKey = customer.getImageUrl();
            if (avatarKey != null && !avatarKey.isBlank()) {
                if (avatarKey.startsWith("http")) {
                    customerAvatarUrl = avatarKey;
                } else {
                    customerAvatarUrl = r2StorageService.getPresignedGetUrl(avatarKey);
                }
            }
        }

        Double originalTotalPrice = request.getOrder() != null ? request.getOrder().getOriginalTotalPrice() : 0.0;
        Double discountAmount = request.getOrder() != null ? request.getOrder().getDiscountAmount() : 0.0;
        Double shippingFee = request.getOrder() != null ? request.getOrder().getShippingFee() : 0.0;
        Double finalPrice = request.getOrder() != null ? request.getOrder().getFinalPrice() : 0.0;

        List<CustomerOrderItemResponse> items = new ArrayList<>();
        if (request.getOrder() != null && request.getOrder().getOrderItems() != null) {
            for (OrderDetail detail : request.getOrder().getOrderItems()) {
                String productName = detail.getProductVariant() != null && detail.getProductVariant().getProduct() != null
                        ? detail.getProductVariant().getProduct().getProductName()
                        : null;
                String variantName = detail.getProductVariant() != null
                        ? detail.getProductVariant().getName()
                        : null;
                String representativeImageKey = detail.getProductVariant() != null && detail.getProductVariant().getProduct() != null
                        ? detail.getProductVariant().getProduct().getRepresentativeImageKey()
                        : null;
                String imgUrl = representativeImageKey != null && !representativeImageKey.isBlank()
                        ? r2StorageService.getPresignedGetUrl(representativeImageKey)
                        : null;

                items.add(CustomerOrderItemResponse.builder()
                        .orderItemId(detail.getId())
                        .productVariantId(detail.getProductVariant() != null ? detail.getProductVariant().getId() : null)
                        .productName(productName)
                        .variantName(variantName)
                        .quantity(detail.getQuantity())
                        .unitPrice(detail.getPriceAtPurchase())
                        .priceAtPurchase(detail.getPriceAtPurchase())
                        .lineTotal(detail.getPriceAtPurchase() * detail.getQuantity())
                        .subtotal(detail.getPriceAtPurchase() * detail.getQuantity())
                        .productImage(imgUrl)
                        .build());
            }
        }

        List<String> urls = new ArrayList<>();
        if (request.getProofFileKeys() != null && !request.getProofFileKeys().isBlank()) {
            for (String key : request.getProofFileKeys().split(",")) {
                String trimmedKey = key.trim();
                if (!trimmedKey.isEmpty()) {
                    urls.add(r2StorageService.getPresignedGetUrl(trimmedKey));
                }
            }
        }

        return ReturnRequestResponse.builder()
                .id(request.getId())
                .orderId(orderId)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .customerAvatarUrl(customerAvatarUrl)
                .reason(request.getReason())
                .details(request.getDetails())
                .proofFileKeys(request.getProofFileKeys())
                .proofFileUrls(urls)
                .status(request.getStatus())
                .resellable(request.isResellable())
                .createdAt(request.getCreatedAt())
                .originalTotalPrice(originalTotalPrice)
                .discountAmount(discountAmount)
                .shippingFee(shippingFee)
                .finalPrice(finalPrice)
                .orderItems(items)
                .build();
    }
}

package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
@Slf4j
public class InternalAiController {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomerRepository customerRepository;
    private final CustomerVoucherRepository customerVoucherRepository;

    @Value("${app.ai.internal-token:zentech_internal_secret_token_123!@}")
    private String internalToken;

    private void verifyToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            log.warn("Unauthorized access attempt to internal AI API. Token received: {}", token);
            throw new SecurityException("Unauthorized: Invalid internal token");
        }
    }

    private Customer resolveCustomer(Map<String, Object> context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        String userIdStr = (String) context.get("userId");
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new IllegalArgumentException("Context userId is required");
        }
        UUID accountId = UUID.fromString(userIdStr);
        return customerRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found for account " + accountId));
    }

    @PostMapping("/products/resolve")
    public ResponseEntity<ApiResponse<List<ResolvedProductDto>>> resolveProducts(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ProductResolveRequest request
    ) {
        verifyToken(token);
        log.info("Resolving products for AI context: productIds={}, variantIds={}", request.getProductIds(), request.getVariantIds());
        
        List<ResolvedProductDto> responseList = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Resolve individual variants
        if (request.getVariantIds() != null) {
            for (UUID variantId : request.getVariantIds()) {
                productVariantRepository.findOrderableById(variantId).ifPresent(variant -> {
                    Product product = variant.getProduct();
                    responseList.add(mapToResolvedProductDto(product, variant, now));
                });
            }
        }

        // 2. Resolve products (if variants are not specified, return all variants of that product)
        if (request.getProductIds() != null) {
            for (UUID productId : request.getProductIds()) {
                productRepository.findProductDetailById(productId).ifPresent(product -> {
                    if (product.getVariants() != null) {
                        for (ProductVariant variant : product.getVariants()) {
                            if (!variant.isDeleted()) {
                                // Prevent duplicates if already resolved by variantId
                                boolean exists = responseList.stream()
                                        .anyMatch(r -> r.getVariantId().equals(variant.getId()));
                                if (!exists) {
                                    responseList.add(mapToResolvedProductDto(product, variant, now));
                                }
                            }
                        }
                    }
                });
            }
        }

        return ResponseEntity.ok(ApiResponse.success(responseList));
    }

    private ResolvedProductDto mapToResolvedProductDto(Product product, ProductVariant variant, Instant now) {
        double activePrice = variant.getOriginalPrice();
        String promoInfo = null;
        if (variant.getSalePrice() != null && variant.getSaleStartAt() != null && variant.getSaleEndAt() != null
                && now.isAfter(variant.getSaleStartAt()) && now.isBefore(variant.getSaleEndAt())) {
            activePrice = variant.getSalePrice();
            promoInfo = String.format("Giảm giá đặc biệt: %,.0f VND (Giá gốc: %,.0f VND)", variant.getSalePrice(), variant.getOriginalPrice());
        }

        double avgRating = 0.0;
        int reviewCount = 0;
        if (product.getReviewList() != null && !product.getReviewList().isEmpty()) {
            avgRating = product.getReviewList().stream().mapToInt(ProductReview::getRating).average().orElse(0.0);
            reviewCount = product.getReviewList().size();
        }

        return ResolvedProductDto.builder()
                .productId(product.getId())
                .variantId(variant.getId())
                .sku("")
                .name(product.getProductName())
                .variantName(variant.getName())
                .price(BigDecimal.valueOf(activePrice))
                .stock(variant.getStockQuantity())
                .promotionInfo(promoInfo)
                .rating(avgRating)
                .reviewCount(reviewCount)
                .build();
    }

    @PostMapping("/orders/resolve")
    public ResponseEntity<ApiResponse<Object>> resolveOrders(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody OrderResolveRequest request
    ) {
        verifyToken(token);
        Customer customer = resolveCustomer(request.getContext());
        
        if (request.getOrderId() != null && !request.getOrderId().isBlank()) {
            // Find specific order
            UUID orderId = UUID.fromString(request.getOrderId());
            Optional<Order> orderOpt = orderRepository.findByIdAndCustomer_Id(orderId, customer.getId());
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.builder().success(false).message("Order not found or access denied").build());
            }
            return ResponseEntity.ok(ApiResponse.success(mapToOrderDetailsDto(orderOpt.get())));
        } else {
            // Return recent orders of the customer
            List<Order> orders = orderRepository.findByCustomerId(customer.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
            List<OrderSummaryDto> summaries = orders.stream().map(this::mapToOrderSummaryDto).toList();
            return ResponseEntity.ok(ApiResponse.success(summaries));
        }
    }

    private OrderSummaryDto mapToOrderSummaryDto(Order order) {
        return OrderSummaryDto.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .finalPrice(BigDecimal.valueOf(order.getFinalPrice()))
                .paymentMethod(order.getPaymentMethod().name())
                .paymentStatus(order.getPaymentStatus().name())
                .orderStatus(order.getOrderStatus().name())
                .build();
    }

    private OrderDetailsDto mapToOrderDetailsDto(Order order) {
        List<OrderItemDto> items = new ArrayList<>();
        if (order.getOrderItems() != null) {
            for (OrderDetail item : order.getOrderItems()) {
                items.add(OrderItemDto.builder()
                        .orderItemId(item.getId())
                        .productName(item.getProductVariant().getProduct().getProductName())
                        .variantName(item.getProductVariant().getName())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(BigDecimal.valueOf(item.getPriceAtPurchase()))
                        .build());
            }
        }

        return OrderDetailsDto.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .originalTotalPrice(BigDecimal.valueOf(order.getOriginalTotalPrice()))
                .discountAmount(BigDecimal.valueOf(order.getDiscountAmount()))
                .shippingFee(BigDecimal.valueOf(order.getShippingFee()))
                .finalPrice(BigDecimal.valueOf(order.getFinalPrice()))
                .paymentMethod(order.getPaymentMethod().name())
                .paymentStatus(order.getPaymentStatus().name())
                .orderStatus(order.getOrderStatus().name())
                .shippingAddress(order.getAddress() != null ? order.getAddress().getStreet() + ", " + order.getAddress().getWard() + ", " + order.getAddress().getProvince() : null)
                .items(items)
                .build();
    }

    @GetMapping("/customers/{userId}/profile")
    public ResponseEntity<ApiResponse<CustomerProfileDto>> getCustomerProfile(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable UUID userId,
            @RequestParam Map<String, Object> context
    ) {
        verifyToken(token);
        Customer customer = customerRepository.findDetailByUserInfo_Id(userId)
                .orElseThrow(() -> new NoSuchElementException("Customer profile not found"));
        
        CustomerProfileDto dto = CustomerProfileDto.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getUserInfo().getEmail())
                .imageUrl(customer.getImageUrl())
                .build();
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/customers/{userId}/vouchers")
    public ResponseEntity<ApiResponse<List<VoucherDto>>> getCustomerVouchers(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable UUID userId,
            @RequestParam Map<String, Object> context
    ) {
        verifyToken(token);
        Customer customer = customerRepository.findByUserInfo_Id(userId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found"));

        List<CustomerVoucher> vouchers = customerVoucherRepository.findAvailableByCustomerId(
                customer.getId(), Instant.now(), org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        List<VoucherDto> dtos = vouchers.stream().map(cv -> {
            Coupon coupon = cv.getCoupon();
            String desc = coupon.getType() == CouponType.PERCENTAGE
                    ? "Giảm " + coupon.getDiscountValue() + "%"
                    : "Giảm " + coupon.getDiscountValue() + " VND";
            return VoucherDto.builder()
                    .voucherId(cv.getId())
                    .code(coupon.getCode())
                    .discountValue(BigDecimal.valueOf(coupon.getDiscountValue()))
                    .couponType(coupon.getType() != null ? coupon.getType().name() : "PERCENTAGE")
                    .description(desc)
                    .endAt(coupon.getEndAt())
                    .build();
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/customers/{userId}/loyalty-points")
    public ResponseEntity<ApiResponse<LoyaltyPointsDto>> getLoyaltyPoints(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable UUID userId,
            @RequestParam Map<String, Object> context
    ) {
        verifyToken(token);
        Customer customer = customerRepository.findByUserInfo_Id(userId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found"));

        // Simulate loyalty points for the demo since points are not mapped in DB
        LoyaltyPointsDto dto = LoyaltyPointsDto.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .points(150)
                .tier("Silver")
                .nextTierPoints(300)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/orders/{orderId}/tracking")
    public ResponseEntity<ApiResponse<OrderTrackingDto>> getOrderTracking(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable UUID orderId,
            @RequestParam Map<String, Object> context
    ) {
        verifyToken(token);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        List<TrackingEventDto> events = new ArrayList<>();
        Instant orderTime = order.getCreatedAt();
        
        events.add(new TrackingEventDto("Đặt hàng thành công", "Đơn hàng đã được ghi nhận trên hệ thống", orderTime));
        
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.CONFIRMED || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            events.add(new TrackingEventDto("Chuẩn bị hàng", "Người bán đang chuẩn bị đóng gói sản phẩm", orderTime.plus(2, ChronoUnit.HOURS)));
        }
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            events.add(new TrackingEventDto("Giao cho ĐVVC", "Đơn hàng đã được chuyển giao cho bưu tá", orderTime.plus(1, ChronoUnit.DAYS)));
            events.add(new TrackingEventDto("Đang vận chuyển", "Đơn hàng đang trên đường trung chuyển đến trạm giao nhận", orderTime.plus(30, ChronoUnit.HOURS)));
        }
        if (status == OrderStatus.COMPLETED) {
            events.add(new TrackingEventDto("Đang giao hàng", "Bưu tá đang liên hệ giao hàng cho bạn", orderTime.plus(2, ChronoUnit.DAYS)));
            events.add(new TrackingEventDto("Giao hàng thành công", "Bạn đã nhận hàng thành công", orderTime.plus(49, ChronoUnit.HOURS)));
        }
        if (status == OrderStatus.CANCELLED) {
            events.add(new TrackingEventDto("Đơn hàng đã hủy", "Đơn hàng đã bị hủy bỏ", orderTime.plus(1, ChronoUnit.HOURS)));
        }

        // Sort events latest first
        events.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        OrderTrackingDto dto = OrderTrackingDto.builder()
                .orderId(orderId)
                .status(status.name())
                .events(events)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/warranties/{orderItemId}")
    public ResponseEntity<ApiResponse<WarrantyDto>> getWarrantyStatus(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable UUID orderItemId,
            @RequestParam Map<String, Object> context
    ) {
        verifyToken(token);
        OrderDetail item = orderDetailRepository.findById(orderItemId)
                .orElseThrow(() -> new NoSuchElementException("Order item not found"));

        Instant purchaseDate = item.getOrder().getCreatedAt();
        // Warranty is active for 1 year (12 months) from purchase date
        Instant warrantyEnd = purchaseDate.plus(365, ChronoUnit.DAYS);
        boolean active = Instant.now().isBefore(warrantyEnd);

        WarrantyDto dto = WarrantyDto.builder()
                .orderItemId(orderItemId)
                .productName(item.getProductVariant().getProduct().getProductName())
                .variantName(item.getProductVariant().getName())
                .purchaseDate(purchaseDate)
                .warrantyEndDate(warrantyEnd)
                .status(active ? "ACTIVE" : "EXPIRED")
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // DTO Definitions
    @Data
    public static class ProductResolveRequest {
        private List<UUID> productIds;
        private List<UUID> variantIds;
        private Map<String, Object> context;
    }

    @Data
    public static class OrderResolveRequest {
        private String orderId;
        private Map<String, Object> context;
    }

    @Data
    @Builder
    public static class ResolvedProductDto {
        private UUID productId;
        private UUID variantId;
        private String sku;
        private String name;
        private String variantName;
        private BigDecimal price;
        private Integer stock;
        private String promotionInfo;
        private Double rating;
        private Integer reviewCount;
    }

    @Data
    @Builder
    public static class OrderSummaryDto {
        private UUID orderId;
        private Instant createdAt;
        private BigDecimal finalPrice;
        private String paymentMethod;
        private String paymentStatus;
        private String orderStatus;
    }

    @Data
    @Builder
    public static class OrderDetailsDto {
        private UUID orderId;
        private Instant createdAt;
        private BigDecimal originalTotalPrice;
        private BigDecimal discountAmount;
        private BigDecimal shippingFee;
        private BigDecimal finalPrice;
        private String paymentMethod;
        private String paymentStatus;
        private String orderStatus;
        private String shippingAddress;
        private List<OrderItemDto> items;
    }

    @Data
    @Builder
    public static class OrderItemDto {
        private UUID orderItemId;
        private String productName;
        private String variantName;
        private int quantity;
        private BigDecimal priceAtPurchase;
    }

    @Data
    @Builder
    public static class CustomerProfileDto {
        private UUID customerId;
        private String fullName;
        private String email;
        private String imageUrl;
    }

    @Data
    @Builder
    public static class VoucherDto {
        private UUID voucherId;
        private String code;
        private BigDecimal discountValue;
        private String couponType;
        private String description;
        private Instant endAt;
    }

    @Data
    @Builder
    public static class LoyaltyPointsDto {
        private UUID customerId;
        private String fullName;
        private int points;
        private String tier;
        private int nextTierPoints;
    }

    @Data
    @Builder
    public static class OrderTrackingDto {
        private UUID orderId;
        private String status;
        private List<TrackingEventDto> events;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrackingEventDto {
        private String title;
        private String description;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class WarrantyDto {
        private UUID orderItemId;
        private String productName;
        private String variantName;
        private Instant purchaseDate;
        private Instant warrantyEndDate;
        private String status;
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.builder().success(false).message(ex.getMessage()).build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.builder().success(false).message(ex.getMessage()).build());
    }
}

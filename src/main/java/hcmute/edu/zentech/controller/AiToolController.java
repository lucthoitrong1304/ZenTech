package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.ProductDetailResponse;
import hcmute.edu.zentech.dto.response.ProductGroupItemResponse;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.CouponType;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.CustomerVoucherRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.repository.ProductReviewRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import hcmute.edu.zentech.repository.ReturnRequestRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.ProductService;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/tools")
@RequiredArgsConstructor
@Slf4j
public class AiToolController {
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomerRepository customerRepository;
    private final CustomerVoucherRepository customerVoucherRepository;
    private final ProductReviewRepository productReviewRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final R2StorageService r2StorageService;
    private final ProductService productService;

    @PostMapping("/products/resolve")
    public ResponseEntity<ApiResponse<List<ResolvedProductDto>>> resolveProducts(
            @RequestBody ProductResolveRequest request
    ) {
        log.info("Resolving products for AI tool: productIds={}, variantIds={}", request.getProductIds(), request.getVariantIds());
        List<ResolvedProductDto> responseList = new ArrayList<>();
        Instant now = Instant.now();

        if (request.getVariantIds() != null) {
            for (UUID variantId : request.getVariantIds()) {
                productVariantRepository.findOrderableById(variantId).ifPresent(variant -> {
                    Product product = variant.getProduct();
                    responseList.add(mapToResolvedProductDto(product, variant, now));
                });
            }
        }

        if (request.getProductIds() != null) {
            for (UUID productId : request.getProductIds()) {
                productRepository.findProductDetailById(productId).ifPresent(product -> {
                    if (product.getVariants() != null) {
                        for (ProductVariant variant : product.getVariants()) {
                            if (!variant.isDeleted()) {
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

    @PostMapping("/orders/resolve")
    public ResponseEntity<ApiResponse<Object>> resolveOrders(@RequestBody OrderResolveRequest request) {
        Customer customer = resolveCurrentCustomer();

        if (request.getOrderId() != null && !request.getOrderId().isBlank()) {
            UUID orderId = UUID.fromString(request.getOrderId());
            Optional<Order> orderOpt = orderRepository.findByIdAndCustomer_Id(orderId, customer.getId());
            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.builder().success(false).message("Order not found or access denied").build());
            }
            return ResponseEntity.ok(ApiResponse.success(mapToOrderDetailsDto(orderOpt.get())));
        }

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Order> orders = orderRepository.findByCustomerId(customer.getId(), pageRequest).getContent();
        List<OrderSummaryDto> summaries = orders.stream().map(this::mapToOrderSummaryDto).toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<List<ProductReviewDto>>> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        if (!productRepository.existsByIdAndDeletedFalse(productId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<List<ProductReviewDto>>builder().success(false).message("Product not found").build());
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 10));
        PageRequest pageRequest = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        List<ProductReviewDto> reviews = productReviewRepository.findByProduct_Id(productId, pageRequest)
                .getContent()
                .stream()
                .map(this::mapToProductReviewDto)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    @GetMapping("/customers/me/profile")
    public ResponseEntity<ApiResponse<CustomerProfileDto>> getCustomerProfile() {
        requireCurrentCustomerRole();
        UUID accountId = requireCurrentAccountId();
        Customer customer = customerRepository.findDetailByUserInfo_Id(accountId)
                .orElseThrow(() -> new NoSuchElementException("Customer profile not found"));

        CustomerProfileDto dto = CustomerProfileDto.builder()
                .customerId(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getUserInfo().getEmail())
                .imageUrl(customer.getImageUrl())
                .registeredAt(customer.getUserInfo().getCreatedAt())
                .build();
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/customers/me/vouchers")
    public ResponseEntity<ApiResponse<List<VoucherDto>>> getCustomerVouchers() {
        Customer customer = resolveCurrentCustomer();
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
                    .maxDiscount(BigDecimal.valueOf(coupon.getMaxDiscount()))
                    .minOrderAmount(BigDecimal.valueOf(coupon.getMinOrderAmount()))
                    .startAt(coupon.getStartAt())
                    .endAt(coupon.getEndAt())
                    .issuedAt(cv.getIssuedAt())
                    .usedAt(cv.getUsedAt())
                    .active(coupon.isActive())
                    .usageLimit(coupon.getUsageLimit())
                    .usedCount(coupon.getUsedCount())
                    .build();
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/customers/me/addresses")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getCustomerAddresses() {
        requireCurrentCustomerRole();
        UUID accountId = requireCurrentAccountId();
        Customer customer = customerRepository.findDetailByUserInfo_Id(accountId)
                .orElseThrow(() -> new NoSuchElementException("Customer profile not found"));

        List<AddressDto> addresses = customer.getAddressList() == null ? List.of() : customer.getAddressList().stream()
                .filter(address -> !address.isDeleted())
                .sorted(Comparator.comparing(Address::isDefault).reversed()
                        .thenComparing(Address::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(address -> AddressDto.builder()
                        .addressId(address.getId())
                        .phoneNumber(address.getPhoneNumber())
                        .province(address.getProvince())
                        .ward(address.getWard())
                        .street(address.getStreet())
                        .isDefault(address.isDefault())
                        .createdAt(address.getCreatedAt())
                        .updatedAt(address.getUpdatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @GetMapping("/customers/me/loyalty-points")
    public ResponseEntity<ApiResponse<LoyaltyPointsDto>> getLoyaltyPoints() {
        Customer customer = resolveCurrentCustomer();
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
    public ResponseEntity<ApiResponse<OrderTrackingDto>> getOrderTracking(@PathVariable UUID orderId) {
        Customer customer = resolveCurrentCustomer();
        Order order = orderRepository.findByIdAndCustomer_Id(orderId, customer.getId())
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        List<TrackingEventDto> events = new ArrayList<>();
        Instant orderTime = order.getCreatedAt();
        events.add(new TrackingEventDto("Đặt hàng thành công", "Đơn hàng đã được ghi nhận trên hệ thống", orderTime));

        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.CONFIRMED || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            events.add(new TrackingEventDto("Chuẩn bị hàng", "Người bán đang chuẩn bị đóng gói sản phẩm", orderTime.plus(2, ChronoUnit.HOURS)));
        }
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            events.add(new TrackingEventDto("Giao cho DVVC", "Đơn hàng đã được chuyển giao cho bưu tá", orderTime.plus(1, ChronoUnit.DAYS)));
            events.add(new TrackingEventDto("Đang vận chuyển", "Đơn hàng đang trên đường trung chuyển đến trạm giao nhận", orderTime.plus(30, ChronoUnit.HOURS)));
        }
        if (status == OrderStatus.COMPLETED) {
            events.add(new TrackingEventDto("Đang giao hàng", "Bưu tá đang liên hệ giao hàng cho bạn", orderTime.plus(2, ChronoUnit.DAYS)));
            events.add(new TrackingEventDto("Giao hàng thành công", "Bạn đã nhận hàng thành công", orderTime.plus(49, ChronoUnit.HOURS)));
        }
        if (status == OrderStatus.CANCELLED) {
            events.add(new TrackingEventDto("Đơn hàng đã hủy", "Đơn hàng đã bị hủy bỏ", orderTime.plus(1, ChronoUnit.HOURS)));
        }
        events.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        OrderTrackingDto dto = OrderTrackingDto.builder()
                .orderId(orderId)
                .status(status.name())
                .events(events)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/warranties/{orderItemId}")
    public ResponseEntity<ApiResponse<WarrantyDto>> getWarrantyStatus(@PathVariable UUID orderItemId) {
        Customer customer = resolveCurrentCustomer();
        OrderDetail item = orderDetailRepository.findByIdAndOrder_Customer_Id(orderItemId, customer.getId())
                .orElseThrow(() -> new NoSuchElementException("Order item not found"));

        Instant purchaseDate = item.getOrder().getCreatedAt();
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

    @GetMapping("/customers/me/returns")
    public ResponseEntity<ApiResponse<List<ReturnRequestDto>>> getCustomerReturns() {
        Customer customer = resolveCurrentCustomer();
        List<ReturnRequestDto> returns = returnRequestRepository.findByOrder_Customer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(request -> ReturnRequestDto.builder()
                        .returnRequestId(request.getId())
                        .orderId(request.getOrder() != null ? request.getOrder().getId() : null)
                        .reason(request.getReason())
                        .details(request.getDetails())
                        .status(request.getStatus() != null ? request.getStatus().name() : null)
                        .resellable(request.isResellable())
                        .createdAt(request.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(returns));
    }

    private Customer resolveCurrentCustomer() {
        UUID accountId = requireCurrentAccountId();
        requireCurrentCustomerRole();
        return customerRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found for account " + accountId));
    }

    private void requireCurrentCustomerRole() {
        CustomUserDetails user = SecurityContextUtils.getCurrentUser();
        boolean isCustomer = user != null && user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
        if (!isCustomer) {
            throw new AccessDeniedException("AI customer tool requires a customer token");
        }
    }

    private UUID requireCurrentAccountId() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return accountId;
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

        StringBuilder relatedBuilder = new StringBuilder();
        List<RelatedProductSummaryDto> relatedList = new ArrayList<>();
        try {
            ProductDetailResponse details = productService.getProductDetail(product.getId());
            if (details.getGroupProducts() != null && !details.getGroupProducts().isEmpty()) {
                relatedBuilder.append("Sản phẩm cùng nhóm (Group Products):\n");
                for (ProductGroupItemResponse gp : details.getGroupProducts()) {
                    relatedBuilder.append(String.format("- %s (ID: %s)\n", gp.getProductName(), gp.getId()));
                    productRepository.findById(gp.getId()).ifPresent(p -> {
                        Optional<ProductVariant> repVarOpt = p.getVariants().stream()
                                .filter(v -> !v.isDeleted())
                                .min(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                        repVarOpt.ifPresent(v -> relatedList.add(RelatedProductSummaryDto.builder()
                                .productId(p.getId())
                                .variantId(v.getId())
                                .name(p.getProductName())
                                .variantName(v.getName())
                                .price(BigDecimal.valueOf(resolveActivePrice(v, now)))
                                .stock(v.getStockQuantity())
                                .imageKey(resolveRepresentativeImageKey(p))
                                .build()));
                    });
                }
            }

            if (details.getSimilarProducts() != null && !details.getSimilarProducts().isEmpty()) {
                if (!relatedBuilder.isEmpty()) {
                    relatedBuilder.append("\n");
                }
                relatedBuilder.append("Sản phẩm tương tự (Similar Products):\n");
                for (CategoryProductListItemResponse sp : details.getSimilarProducts()) {
                    double price = sp.getSalePrice() != null ? sp.getSalePrice() : sp.getOriginalPrice();
                    relatedBuilder.append(String.format("- %s - Giá: %,.0f VND - Tồn kho: %d sản phẩm (ID: %s)\n",
                            sp.getProductName(), price, sp.getStockQuantity(), sp.getId()));
                    productRepository.findById(sp.getId()).ifPresent(p -> relatedList.add(RelatedProductSummaryDto.builder()
                            .productId(sp.getId())
                            .variantId(null)
                            .name(sp.getProductName())
                            .variantName(null)
                            .price(BigDecimal.valueOf(price))
                            .stock(sp.getStockQuantity())
                            .imageKey(resolveRepresentativeImageKey(p))
                            .build()));
                }
            }
        } catch (Exception ex) {
            log.warn("Error fetching related products for product {}", product.getId(), ex);
        }

        String relatedProductsStr = !relatedBuilder.isEmpty() ? relatedBuilder.toString() : "Không có sản phẩm liên quan nào.";

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
                .imageKey(resolveRepresentativeImageKey(product))
                .specifications(product.getSpecifications())
                .compatibility(product.getCompatibility())
                .boxContents(product.getBoxContents())
                .supportInfo(product.getSupportInfo())
                .relatedProducts(relatedProductsStr)
                .relatedProductList(relatedList)
                .build();
    }

    private double resolveActivePrice(ProductVariant variant, Instant now) {
        if (variant.getSalePrice() != null && variant.getSaleStartAt() != null && variant.getSaleEndAt() != null
                && now.isAfter(variant.getSaleStartAt()) && now.isBefore(variant.getSaleEndAt())) {
            return variant.getSalePrice();
        }
        return variant.getOriginalPrice();
    }

    private String resolveRepresentativeImageKey(Product product) {
        if (product.getRepresentativeImageKey() != null && !product.getRepresentativeImageKey().isBlank()) {
            return product.getRepresentativeImageKey();
        }
        return product.getImageKeys() == null ? null : product.getImageKeys().stream()
                .filter(key -> key != null && !key.isBlank())
                .findFirst()
                .orElse(null);
    }

    private ProductReviewDto mapToProductReviewDto(ProductReview review) {
        List<String> imageUrls = review.getImageKeys() == null ? List.of() : review.getImageKeys().stream()
                .map(r2StorageService::getPresignedGetUrl)
                .filter(Objects::nonNull)
                .toList();

        String videoUrl = review.getVideoKey() == null || review.getVideoKey().isBlank()
                ? null
                : r2StorageService.getPresignedGetUrl(review.getVideoKey());

        return ProductReviewDto.builder()
                .reviewId(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .customerName(review.getCustomer() != null ? review.getCustomer().getFullName() : null)
                .createdAt(review.getCreatedAt())
                .imageUrls(imageUrls)
                .videoUrl(videoUrl)
                .build();
    }

    private OrderSummaryDto mapToOrderSummaryDto(Order order) {
        return OrderSummaryDto.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .finalPrice(BigDecimal.valueOf(order.getFinalPrice()))
                .paymentMethod(order.getPaymentMethod().name())
                .paymentStatus(order.getPaymentStatus().name())
                .orderStatus(order.getOrderStatus().name())
                .items(mapToOrderItemDtos(order))
                .coupons(mapToOrderCouponDtos(order))
                .build();
    }

    private OrderDetailsDto mapToOrderDetailsDto(Order order) {
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
                .items(mapToOrderItemDtos(order))
                .coupons(mapToOrderCouponDtos(order))
                .build();
    }

    private List<OrderItemDto> mapToOrderItemDtos(Order order) {
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
        return items;
    }

    private List<OrderCouponDto> mapToOrderCouponDtos(Order order) {
        if (order.getOrderCoupons() == null) {
            return List.of();
        }

        return order.getOrderCoupons().stream()
                .map(coupon -> OrderCouponDto.builder()
                        .couponCode(coupon.getCouponCode())
                        .couponType(coupon.getCouponType() != null ? coupon.getCouponType().name() : null)
                        .discountValue(coupon.getDiscountValue() == null ? null : BigDecimal.valueOf(coupon.getDiscountValue()))
                        .maxDiscount(coupon.getMaxDiscount() == null ? null : BigDecimal.valueOf(coupon.getMaxDiscount()))
                        .appliedAmount(coupon.getAppliedAmount() == null ? null : BigDecimal.valueOf(coupon.getAppliedAmount()))
                        .build())
                .toList();
    }

    @Data
    public static class ProductResolveRequest {
        private List<UUID> productIds;
        private List<UUID> variantIds;
    }

    @Data
    public static class OrderResolveRequest {
        private String orderId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedProductSummaryDto {
        private UUID productId;
        private UUID variantId;
        private String name;
        private String variantName;
        private BigDecimal price;
        private Integer stock;
        private String imageKey;
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
        private String imageKey;
        private String specifications;
        private String compatibility;
        private String boxContents;
        private String supportInfo;
        private String relatedProducts;
        private List<RelatedProductSummaryDto> relatedProductList;
    }

    @Data
    @Builder
    public static class CustomerProfileDto {
        private UUID customerId;
        private String fullName;
        private String email;
        private String imageUrl;
        private Instant registeredAt;
    }

    @Data
    @Builder
    public static class ProductReviewDto {
        private UUID reviewId;
        private Integer rating;
        private String comment;
        private String customerName;
        private Instant createdAt;
        private List<String> imageUrls;
        private String videoUrl;
    }

    @Data
    @Builder
    public static class AddressDto {
        private UUID addressId;
        private String phoneNumber;
        private String province;
        private String ward;
        private String street;
        private boolean isDefault;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class VoucherDto {
        private UUID voucherId;
        private String code;
        private BigDecimal discountValue;
        private String couponType;
        private String description;
        private BigDecimal maxDiscount;
        private BigDecimal minOrderAmount;
        private Instant startAt;
        private Instant endAt;
        private Instant issuedAt;
        private Instant usedAt;
        private boolean active;
        private int usageLimit;
        private int usedCount;
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
    public static class OrderSummaryDto {
        private UUID orderId;
        private Instant createdAt;
        private BigDecimal finalPrice;
        private String paymentMethod;
        private String paymentStatus;
        private String orderStatus;
        private List<OrderItemDto> items;
        private List<OrderCouponDto> coupons;
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
        private List<OrderCouponDto> coupons;
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
    public static class OrderCouponDto {
        private String couponCode;
        private String couponType;
        private BigDecimal discountValue;
        private BigDecimal maxDiscount;
        private BigDecimal appliedAmount;
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

    @Data
    @Builder
    public static class ReturnRequestDto {
        private UUID returnRequestId;
        private UUID orderId;
        private String reason;
        private String details;
        private String status;
        private boolean resellable;
        private Instant createdAt;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.builder().success(false).message(ex.getMessage()).build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.builder().success(false).message(ex.getMessage()).build());
    }
}

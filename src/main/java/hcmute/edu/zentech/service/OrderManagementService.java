package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.OrderCreateRequest;
import hcmute.edu.zentech.dto.request.OrderUpdateRequest;
import hcmute.edu.zentech.dto.response.OrderManagementDetailResponse;
import hcmute.edu.zentech.dto.response.OrderManagementSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.OrderManagementMapper;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.NotificationType;
import hcmute.edu.zentech.repository.AddressRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import hcmute.edu.zentech.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderManagementService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_ORDER_SORT = "createdAt,desc";

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AddressRepository addressRepository;
    private final R2StorageService r2StorageService;
    private final OrderManagementMapper orderManagementMapper;
    private final NotificationService notificationService;

    public PageResponse<OrderManagementSummaryResponse> getOrders(
            int page,
            int size,
            String sort,
            String keyword,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            Instant startDate,
            Instant endDate
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildOrderSort(sort));
        Page<Order> orderPage = orderRepository.searchManagementOrders(
                normalizeKeyword(keyword),
                orderStatus,
                paymentStatus,
                startDate,
                endDate,
                pageable
        );
        List<OrderDetail> orderDetails = getOrderDetails(orderPage.getContent().stream().map(Order::getId).toList());
        Map<UUID, List<OrderDetail>> orderDetailsMap = groupOrderDetailsByOrderId(orderDetails);

        List<OrderManagementSummaryResponse> content = orderPage.getContent().stream()
                .map(order -> orderManagementMapper.toSummaryResponse(
                        order,
                        orderDetailsMap.getOrDefault(order.getId(), List.of())
                ))
                .toList();

        return PageResponse.from(orderPage, content);
    }

    public OrderManagementDetailResponse getOrderDetail(UUID orderId) {
        Order order = getOrderWithDetail(orderId);
        List<OrderDetail> orderDetails = getOrderDetails(List.of(orderId));
        return orderManagementMapper.toDetailResponse(order, orderDetails, getProductImageUrls(orderDetails));
    }

    @Transactional
    public OrderManagementDetailResponse createOrder(OrderCreateRequest request) {
        Customer customer = getCustomer(request.getCustomerId());
        Address address = getOwnedActiveAddress(customer, request.getAddressId());

        Order order = new Order();
        order.setCustomer(customer);
        order.setAddress(address);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderStatus(OrderStatus.CREATED);
        order.setShippingFee(request.getShippingFee());
        order.setDiscountAmount(0D);

        List<OrderDetail> orderDetails = request.getItems().stream()
                .map(item -> buildOrderDetail(order, item))
                .toList();

        double originalTotalPrice = orderDetails.stream()
                .mapToDouble(orderDetail -> orderDetail.getPriceAtPurchase() * orderDetail.getQuantity())
                .sum();

        order.setOriginalTotalPrice(originalTotalPrice);
        order.setFinalPrice(originalTotalPrice + order.getShippingFee());
        order.setOrderItems(new HashSet<>(orderDetails));

        Order savedOrder = orderRepository.save(order);
        return getOrderDetail(savedOrder.getId());
    }

    @Transactional
    public OrderManagementDetailResponse updateOrder(UUID orderId, OrderUpdateRequest request) {
        Order order = getOrderWithDetail(orderId);
        List<OrderDetail> orderDetails = getOrderDetails(List.of(orderId));

        if (request.getOrderStatus() != null) {
            updateOrderStatus(order, orderDetails, request.getOrderStatus());
        }

        if (request.getPaymentStatus() != null) {
            order.setPaymentStatus(request.getPaymentStatus());
        }

        if (request.getShippingFee() != null) {
            order.setShippingFee(request.getShippingFee());
            recalculateFinalPrice(order);
        }

        if (request.getCustomerName() != null && order.getCustomer() != null) {
            order.getCustomer().setFullName(request.getCustomerName().trim());
        }

        if (request.getShippingAddress() != null && !request.getShippingAddress().isBlank()) {
            Address currentAddress = order.getAddress();
            if (currentAddress == null) {
                currentAddress = new Address();
            } else {
                Address newAddress = new Address();
                newAddress.setPhoneNumber(currentAddress.getPhoneNumber());
                newAddress.setDefault(false);
                newAddress.setDeleted(false);
                currentAddress = newAddress;
            }

            String fullAddress = request.getShippingAddress().trim();
            String[] parts = fullAddress.split(",");
            if (parts.length >= 3) {
                String province = parts[parts.length - 1].trim();
                String ward = parts[parts.length - 2].trim();
                
                StringBuilder streetBuilder = new StringBuilder();
                for (int i = 0; i < parts.length - 2; i++) {
                    if (i > 0) {
                        streetBuilder.append(",");
                    }
                    streetBuilder.append(parts[i]);
                }
                currentAddress.setStreet(streetBuilder.toString().trim());
                currentAddress.setWard(ward);
                currentAddress.setProvince(province);
            } else {
                currentAddress.setStreet(fullAddress);
                currentAddress.setWard("");
                currentAddress.setProvince("");
            }

            Address savedAddress = addressRepository.save(currentAddress);
            order.setAddress(savedAddress);
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (OrderUpdateRequest.OrderItemUpdateRequest itemRequest : request.getItems()) {
                OrderDetail detail = orderDetails.stream()
                        .filter(d -> d.getId().equals(itemRequest.getOrderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("OrderDetail", "id", itemRequest.getOrderItemId()));
                
                int oldQty = detail.getQuantity();
                int newQty = itemRequest.getQuantity();
                if (newQty <= 0) {
                    throw new RuntimeException("Quantity must be greater than 0");
                }
                if (oldQty != newQty) {
                    int diff = newQty - oldQty;
                    ProductVariant variant = detail.getProductVariant();
                    if (variant != null) {
                        if (diff > 0 && variant.getStockQuantity() < diff) {
                            throw new RuntimeException("Product variant stock is not enough");
                        }
                        variant.setStockQuantity(variant.getStockQuantity() - diff);
                        productVariantRepository.save(variant);
                    }
                    detail.setQuantity(newQty);
                    orderDetailRepository.save(detail);
                }
            }

            double originalTotalPrice = orderDetails.stream()
                    .mapToDouble(d -> d.getPriceAtPurchase() * d.getQuantity())
                    .sum();
            order.setOriginalTotalPrice(originalTotalPrice);
            recalculateFinalPrice(order);
        }

        return orderManagementMapper.toDetailResponse(order, orderDetails, getProductImageUrls(orderDetails));
    }

    @Transactional
    public OrderManagementDetailResponse cancelOrder(UUID orderId) {
        Order order = getOrderWithDetail(orderId);
        List<OrderDetail> orderDetails = getOrderDetails(List.of(orderId));
        cancelOrder(order, orderDetails);

        return orderManagementMapper.toDetailResponse(order, orderDetails, getProductImageUrls(orderDetails));
    }

    private Customer getCustomer(UUID customerId) {
        Customer customer = customerRepository.findDetailById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        if (customer.getUserInfo() == null || customer.getUserInfo().getRole() != Role.CUSTOMER) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }

        return customer;
    }

    private Address getOwnedActiveAddress(Customer customer, UUID addressId) {
        if (customer.getAddressList() == null || customer.getAddressList().isEmpty()) {
            throw new ResourceNotFoundException("Address", "id", addressId);
        }

        return customer.getAddressList().stream()
                .filter(address -> address.getId() != null && address.getId().equals(addressId))
                .filter(address -> !address.isDeleted())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
    }

    private Order getOrderWithDetail(UUID orderId) {
        return orderRepository.findManagementDetailById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }

    private OrderDetail buildOrderDetail(Order order, OrderCreateRequest.OrderCreateItemRequest item) {
        ProductVariant productVariant = productVariantRepository.findOrderableById(item.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product Variant", "id", item.getProductVariantId()));

        if (productVariant.getStockQuantity() < item.getQuantity()) {
            throw new RuntimeException("Product variant stock is not enough");
        }

        productVariant.setStockQuantity(productVariant.getStockQuantity() - item.getQuantity());

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrder(order);
        orderDetail.setProductVariant(productVariant);
        orderDetail.setQuantity(item.getQuantity());
        orderDetail.setPriceAtPurchase(getEffectivePrice(productVariant));
        return orderDetail;
    }

    private double getEffectivePrice(ProductVariant productVariant) {
        return productVariant.getSalePrice() != null ? productVariant.getSalePrice() : productVariant.getOriginalPrice();
    }

    private void restoreStock(List<OrderDetail> orderDetails) {
        orderDetails.forEach(orderDetail -> {
            ProductVariant productVariant = orderDetail.getProductVariant();
            if (productVariant != null) {
                productVariant.setStockQuantity(productVariant.getStockQuantity() + orderDetail.getQuantity());
            }
        });
    }

    private void updateOrderStatus(Order order, List<OrderDetail> orderDetails, OrderStatus nextStatus) {
        if (nextStatus == OrderStatus.CANCELLED) {
            cancelOrder(order, orderDetails);
            return;
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new RuntimeException("Cancelled order cannot be reopened");
        }

        OrderStatus prevStatus = order.getOrderStatus();
        order.setOrderStatus(nextStatus);

        // Notify customer when status changes
        if (prevStatus != nextStatus && order.getCustomer() != null && order.getCustomer().getUserInfo() != null) {
            String title = "Cập nhật trạng thái đơn hàng";
            String content = String.format("Đơn hàng #%s của bạn đã được cập nhật trạng thái thành: %s.", 
                    order.getId(), nextStatus);
            notificationService.createNotification(
                    order.getCustomer().getUserInfo().getId(),
                    title,
                    content,
                    NotificationType.ORDER_STATUS,
                    order.getId()
            );
        }
    }

    private void cancelOrder(Order order, List<OrderDetail> orderDetails) {
        if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            throw new RuntimeException("Completed order cannot be cancelled");
        }

        if (order.getOrderStatus() != OrderStatus.CANCELLED) {
            restoreStock(orderDetails);
            order.setOrderStatus(OrderStatus.CANCELLED);

            // Notify customer about cancellation
            if (order.getCustomer() != null && order.getCustomer().getUserInfo() != null) {
                String title = "Đơn hàng đã bị hủy";
                String content = String.format("Đơn hàng #%s của bạn đã bị hủy.", order.getId());
                notificationService.createNotification(
                        order.getCustomer().getUserInfo().getId(),
                        title,
                        content,
                        NotificationType.ORDER_STATUS,
                        order.getId()
                );
            }
        }
    }

    private void recalculateFinalPrice(Order order) {
        order.setFinalPrice(order.getOriginalTotalPrice() - order.getDiscountAmount() + order.getShippingFee());
    }

    private List<OrderDetail> getOrderDetails(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        return orderDetailRepository.findByOrder_IdIn(orderIds);
    }

    private Map<UUID, List<OrderDetail>> groupOrderDetailsByOrderId(List<OrderDetail> orderDetails) {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return Collections.emptyMap();
        }

        return orderDetails.stream()
                .collect(Collectors.groupingBy(orderDetail -> orderDetail.getOrder().getId()));
    }

    private Map<UUID, String> getProductImageUrls(List<OrderDetail> orderDetails) {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return Collections.emptyMap();
        }

        return orderDetails.stream()
                .map(orderDetail -> new OrderDetailImage(orderDetail, getProductImageUrl(orderDetail)))
                .filter(detailImage -> detailImage.orderDetail().getId() != null && detailImage.imageUrl() != null)
                .collect(Collectors.toMap(
                        detailImage -> detailImage.orderDetail().getId(),
                        OrderDetailImage::imageUrl,
                        (left, right) -> left
                ));
    }

    private String getProductImageUrl(OrderDetail orderDetail) {
        if (orderDetail.getProductVariant() == null || orderDetail.getProductVariant().getProduct() == null) {
            return null;
        }

        String representativeImageKey = orderDetail.getProductVariant().getProduct().getRepresentativeImageKey();
        if (representativeImageKey == null || representativeImageKey.isBlank()) {
            return null;
        }

        return r2StorageService.getPresignedGetUrl(representativeImageKey);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    private Sort buildOrderSort(String sort) {
        return buildSort(sort, DEFAULT_ORDER_SORT, Map.of(
                "createdAt", "createdAt",
                "finalPrice", "finalPrice",
                "orderStatus", "orderStatus",
                "paymentStatus", "paymentStatus"
        ));
    }

    private Sort buildSort(String sort, String defaultSort, Map<String, String> sortableFields) {
        String sortValue = (sort == null || sort.isBlank()) ? defaultSort : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "asc";

        String defaultField = defaultSort.split(",", 2)[0];
        String mappedField = sortableFields.getOrDefault(requestedField, sortableFields.get(defaultField));
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(
                new Sort.Order(direction, mappedField),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
    }

    private record OrderDetailImage(OrderDetail orderDetail, String imageUrl) {
    }
}

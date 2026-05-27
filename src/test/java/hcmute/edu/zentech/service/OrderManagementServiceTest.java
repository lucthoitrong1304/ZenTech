package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.OrderCreateRequest;
import hcmute.edu.zentech.dto.request.OrderUpdateRequest;
import hcmute.edu.zentech.dto.response.OrderManagementDetailResponse;
import hcmute.edu.zentech.dto.response.OrderManagementSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.OrderManagementMapper;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentMethod;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderManagementServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private R2StorageService r2StorageService;

    private OrderManagementService orderManagementService;

    @BeforeEach
    void setUp() {
        orderManagementService = new OrderManagementService(
                orderRepository,
                orderDetailRepository,
                customerRepository,
                productVariantRepository,
                r2StorageService,
                new OrderManagementMapper()
        );
    }

    @Test
    void getOrdersReturnsPagedSummariesWithFiltersAndItems() {
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, OrderStatus.CREATED);
        OrderDetail orderDetail = createOrderDetail(order, createVariant(UUID.randomUUID(), 10, 120000D, null), 2, 120000D);

        when(orderRepository.searchManagementOrders(eq("alice"), eq(OrderStatus.CREATED), eq(PaymentStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(orderDetail));

        PageResponse<OrderManagementSummaryResponse> response = orderManagementService.getOrders(
                0,
                10,
                "createdAt,desc",
                " alice ",
                OrderStatus.CREATED,
                PaymentStatus.PENDING
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getOrderId()).isEqualTo(orderId);
        assertThat(response.getContent().get(0).getItemCount()).isEqualTo(1);
        assertThat(response.getContent().get(0).getCustomer().getEmail()).isEqualTo("alice@example.com");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).searchManagementOrders(eq("alice"), eq(OrderStatus.CREATED), eq(PaymentStatus.PENDING), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void getOrderDetailReturnsFullOrderData() {
        UUID orderId = UUID.randomUUID();
        Product product = new Product();
        product.setProductName("Keyboard");
        product.setRepresentativeImageKey("products/keyboard.png");
        ProductVariant variant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .name("Black")
                .product(product)
                .build();
        Order order = createOrder(orderId, OrderStatus.CONFIRMED);
        OrderDetail orderDetail = createOrderDetail(order, variant, 1, 150000D);

        when(orderRepository.findManagementDetailById(orderId)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(orderDetail));
        when(r2StorageService.getPresignedGetUrl("products/keyboard.png")).thenReturn("https://cdn.example.com/keyboard.png");

        OrderManagementDetailResponse response = orderManagementService.getOrderDetail(orderId);

        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getCustomer().getFullName()).isEqualTo("Alice Nguyen");
        assertThat(response.getShippingAddress().getAddressId()).isEqualTo(order.getAddress().getId());
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductImage()).isEqualTo("https://cdn.example.com/keyboard.png");
    }

    @Test
    void createOrderCalculatesTotalsAndDecreasesStock() {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, addressId, Role.CUSTOMER);
        ProductVariant variant = createVariant(variantId, 5, 200000D, 150000D);
        OrderCreateRequest request = createRequest(customerId, addressId, variantId, 2, 20000D);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderRepository.findManagementDetailById(any(UUID.class))).thenAnswer(invocation -> {
            UUID orderId = invocation.getArgument(0);
            Order order = createOrder(orderId, OrderStatus.CREATED);
            order.setCustomer(customer);
            order.setAddress(customer.getAddressList().iterator().next());
            order.setPaymentMethod(PaymentMethod.CASH);
            order.setPaymentStatus(PaymentStatus.PENDING);
            order.setOriginalTotalPrice(300000D);
            order.setDiscountAmount(0D);
            order.setShippingFee(20000D);
            order.setFinalPrice(320000D);
            return Optional.of(order);
        });

        OrderManagementDetailResponse response = orderManagementService.createOrder(request);

        assertThat(variant.getStockQuantity()).isEqualTo(3);
        assertThat(response.getOriginalTotalPrice()).isEqualTo(300000D);
        assertThat(response.getFinalPrice()).isEqualTo(320000D);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedOrder.getOrderItems()).hasSize(1);
    }

    @Test
    void createOrderThrowsWhenAddressDoesNotBelongToCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, UUID.randomUUID(), Role.CUSTOMER);
        OrderCreateRequest request = createRequest(customerId, UUID.randomUUID(), UUID.randomUUID(), 1, 0D);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> orderManagementService.createOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Address");
    }

    @Test
    void createOrderThrowsWhenStockIsNotEnough() {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, addressId, Role.CUSTOMER);
        ProductVariant variant = createVariant(variantId, 1, 100000D, null);
        OrderCreateRequest request = createRequest(customerId, addressId, variantId, 2, 0D);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> orderManagementService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("stock");
    }

    @Test
    void updateOrderChangesStatusesAndRecalculatesFinalPrice() {
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, OrderStatus.CREATED);
        order.setOriginalTotalPrice(300000D);
        order.setDiscountAmount(50000D);
        order.setShippingFee(10000D);
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setOrderStatus(OrderStatus.CONFIRMED);
        request.setPaymentStatus(PaymentStatus.SUCCESS);
        request.setShippingFee(20000D);

        when(orderRepository.findManagementDetailById(orderId)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of());

        OrderManagementDetailResponse response = orderManagementService.updateOrder(orderId, request);

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getFinalPrice()).isEqualTo(270000D);
    }

    @Test
    void updateOrderToCancelledRestoresStock() {
        UUID orderId = UUID.randomUUID();
        ProductVariant variant = createVariant(UUID.randomUUID(), 4, 100000D, null);
        Order order = createOrder(orderId, OrderStatus.CONFIRMED);
        OrderDetail orderDetail = createOrderDetail(order, variant, 3, 100000D);
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setOrderStatus(OrderStatus.CANCELLED);

        when(orderRepository.findManagementDetailById(orderId)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(orderDetail));

        OrderManagementDetailResponse response = orderManagementService.updateOrder(orderId, request);

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(variant.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void cancelOrderSoftCancelsAndRestoresStock() {
        UUID orderId = UUID.randomUUID();
        ProductVariant variant = createVariant(UUID.randomUUID(), 3, 100000D, null);
        Order order = createOrder(orderId, OrderStatus.SHIPPED);
        OrderDetail orderDetail = createOrderDetail(order, variant, 2, 100000D);

        when(orderRepository.findManagementDetailById(orderId)).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(orderDetail));

        OrderManagementDetailResponse response = orderManagementService.cancelOrder(orderId);

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(variant.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void cancelOrderThrowsWhenCompleted() {
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, OrderStatus.COMPLETED);

        when(orderRepository.findManagementDetailById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderManagementService.cancelOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Completed");
    }

    private OrderCreateRequest createRequest(UUID customerId, UUID addressId, UUID variantId, int quantity, double shippingFee) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setCustomerId(customerId);
        request.setAddressId(addressId);
        request.setShippingFee(shippingFee);
        request.setPaymentMethod(PaymentMethod.CASH);

        OrderCreateRequest.OrderCreateItemRequest item = new OrderCreateRequest.OrderCreateItemRequest();
        item.setProductVariantId(variantId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    private Order createOrder(UUID orderId, OrderStatus orderStatus) {
        Customer customer = createCustomer(UUID.randomUUID(), UUID.randomUUID(), Role.CUSTOMER);
        Order order = new Order();
        order.setId(orderId);
        order.setCreatedAt(Instant.parse("2026-04-15T10:15:30Z"));
        order.setOrderStatus(orderStatus);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setOriginalTotalPrice(200000D);
        order.setDiscountAmount(0D);
        order.setShippingFee(15000D);
        order.setFinalPrice(215000D);
        order.setCustomer(customer);
        order.setAddress(customer.getAddressList().iterator().next());
        return order;
    }

    private Customer createCustomer(UUID customerId, UUID addressId, Role role) {
        AccountUser accountUser = AccountUser.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .password("secret")
                .role(role)
                .isActive(true)
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();

        Address address = new Address();
        address.setId(addressId);
        address.setPhoneNumber("0909000999");
        address.setProvince("Ho Chi Minh");
        address.setWard("Ward 1");
        address.setStreet("123 Nguyen Hue");
        address.setDeleted(false);

        return Customer.builder()
                .id(customerId)
                .fullName("Alice Nguyen")
                .userInfo(accountUser)
                .addressList(Set.of(address))
                .build();
    }

    private ProductVariant createVariant(UUID variantId, int stockQuantity, double originalPrice, Double salePrice) {
        Product product = new Product();
        product.setProductName("Keyboard");

        return ProductVariant.builder()
                .id(variantId)
                .name("Black")
                .stockQuantity(stockQuantity)
                .originalPrice(originalPrice)
                .salePrice(salePrice)
                .product(product)
                .build();
    }

    private OrderDetail createOrderDetail(Order order, ProductVariant variant, int quantity, double priceAtPurchase) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setId(UUID.randomUUID());
        orderDetail.setOrder(order);
        orderDetail.setProductVariant(variant);
        orderDetail.setQuantity(quantity);
        orderDetail.setPriceAtPurchase(priceAtPurchase);
        return orderDetail;
    }
}

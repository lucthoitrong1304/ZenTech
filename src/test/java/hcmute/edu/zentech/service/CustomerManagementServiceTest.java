package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.CustomerManagementMapper;
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
import hcmute.edu.zentech.repository.projection.CustomerOrderAggregateProjection;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerManagementServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private R2StorageService r2StorageService;

    private CustomerManagementService customerManagementService;

    @BeforeEach
    void setUp() {
        customerManagementService = new CustomerManagementService(
                customerRepository,
                orderRepository,
                orderDetailRepository,
                r2StorageService,
                new CustomerManagementMapper()
        );
    }

    @Test
    void getCustomersReturnsPagedSummariesWithAggregates() {
        Customer firstCustomer = createCustomer("Alice Nguyen", "alice@example.com", true, Instant.parse("2026-04-15T10:15:30Z"));
        Customer secondCustomer = createCustomer("Bob Tran", "bob@example.com", false, Instant.parse("2026-04-14T10:15:30Z"));

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(customerRepository.searchCustomers(eq("alice"), eq(true), eq(Role.CUSTOMER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstCustomer, secondCustomer), pageRequest, 2));
        when(orderRepository.findCustomerOrderAggregates(anyList(), eq(OrderStatus.CANCELLED), eq(OrderStatus.COMPLETED), eq(PaymentStatus.REFUNDED), eq(PaymentStatus.SUCCESS)))
                .thenReturn(List.of(new AggregateProjection(firstCustomer.getId(), 3L, 1500000D, Instant.parse("2026-04-16T08:00:00Z"))));

        PageResponse<CustomerSummaryResponse> response = customerManagementService.getCustomers(0, 10, "registeredAt,desc", "  alice  ", true);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getCustomerId()).isEqualTo(firstCustomer.getId());
        assertThat(response.getContent().get(0).getTotalOrders()).isEqualTo(3L);
        assertThat(response.getContent().get(0).getTotalSpent()).isEqualTo(1500000D);
        assertThat(response.getContent().get(1).getTotalOrders()).isZero();
        assertThat(response.getContent().get(1).getTotalSpent()).isZero();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).searchCustomers(eq("alice"), eq(true), eq(Role.CUSTOMER), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userInfo.createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("userInfo.createdAt").isDescending()).isTrue();
    }

    @Test
    void getCustomerDetailReturnsProfileAndSummary() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer("Alice Nguyen", "alice@example.com", true, Instant.parse("2026-04-15T10:15:30Z"));
        customer.setId(customerId);

        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setProvince("Ho Chi Minh");
        address.setWard("Ward 1");
        address.setStreet("123 Nguyen Hue");
        address.setPhoneNumber("0909000999");
        address.setDefault(true);
        customer.setAddressList(Set.of(address));

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findCustomerOrderAggregates(anyList(), eq(OrderStatus.CANCELLED), eq(OrderStatus.COMPLETED), eq(PaymentStatus.REFUNDED), eq(PaymentStatus.SUCCESS)))
                .thenReturn(List.of(new AggregateProjection(customerId, 2L, 500000D, Instant.parse("2026-04-15T12:00:00Z"))));

        CustomerDetailResponse response = customerManagementService.getCustomerDetail(customerId);

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getAddressList()).hasSize(1);
        assertThat(response.getTotalOrders()).isEqualTo(2L);
        assertThat(response.getTotalSpent()).isEqualTo(500000D);
    }

    @Test
    void getCustomerDetailThrowsWhenCustomerDoesNotExist() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerManagementService.getCustomerDetail(customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void updateCustomerStatusActivatesCustomerAccount() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer("Alice Nguyen", "alice@example.com", false, Instant.parse("2026-04-15T10:15:30Z"));
        customer.setId(customerId);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findCustomerOrderAggregates(anyList(), eq(OrderStatus.CANCELLED), eq(OrderStatus.COMPLETED), eq(PaymentStatus.REFUNDED), eq(PaymentStatus.SUCCESS)))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerManagementService.updateCustomerStatus(customerId, true);

        assertThat(customer.getUserInfo().isActive()).isTrue();
        assertThat(response.isActive()).isTrue();
        assertThat(response.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void updateCustomerStatusLocksCustomerAccount() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer("Bob Tran", "bob@example.com", true, Instant.parse("2026-04-15T10:15:30Z"));
        customer.setId(customerId);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findCustomerOrderAggregates(anyList(), eq(OrderStatus.CANCELLED), eq(OrderStatus.COMPLETED), eq(PaymentStatus.REFUNDED), eq(PaymentStatus.SUCCESS)))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerManagementService.updateCustomerStatus(customerId, false);

        assertThat(customer.getUserInfo().isActive()).isFalse();
        assertThat(response.isActive()).isFalse();
        assertThat(response.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void updateCustomerStatusThrowsWhenCustomerDoesNotExist() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerManagementService.updateCustomerStatus(customerId, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void updateCustomerStatusThrowsWhenAccountRoleIsNotCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer("Owner User", "owner@example.com", true, Instant.parse("2026-04-15T10:15:30Z"), Role.OWNER);
        customer.setId(customerId);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerManagementService.updateCustomerStatus(customerId, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void updateCustomerStatusThrowsWhenAccountRoleIsAdmin() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer("Admin User", "admin@example.com", true, Instant.parse("2026-04-15T10:15:30Z"), Role.ADMIN);
        customer.setId(customerId);

        when(customerRepository.findDetailById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerManagementService.updateCustomerStatus(customerId, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void getCustomerOrdersReturnsPagedHistoryWithItems() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        Product product = new Product();
        product.setProductName("Keyboard");
        product.setRepresentativeImageKey("products/keyboard/main.png");

        ProductVariant productVariant = ProductVariant.builder()
                .id(variantId)
                .name("Black")
                .product(product)
                .build();

        Order order = new Order();
        order.setId(orderId);
        order.setCreatedAt(Instant.parse("2026-04-15T11:00:00Z"));
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setFinalPrice(250000D);
        order.setDiscountAmount(10000D);
        order.setShippingFee(15000D);

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setId(UUID.randomUUID());
        orderDetail.setOrder(order);
        orderDetail.setProductVariant(productVariant);
        orderDetail.setQuantity(2);
        orderDetail.setPriceAtPurchase(125000D);

        when(customerRepository.existsById(customerId)).thenReturn(true);
        when(orderRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(orderDetail));
        when(r2StorageService.getPresignedGetUrl("products/keyboard/main.png"))
                .thenReturn("https://cdn.example.com/products/keyboard/main.png");

        PageResponse<CustomerOrderHistoryResponse> response = customerManagementService.getCustomerOrders(customerId, 0, 10, "createdAt,desc");

        assertThat(response.getContent()).hasSize(1);
        CustomerOrderHistoryResponse orderResponse = response.getContent().get(0);
        assertThat(orderResponse.getOrderId()).isEqualTo(orderId);
        assertThat(orderResponse.getItems()).hasSize(1);
        assertThat(orderResponse.getItems().get(0).getProductVariantId()).isEqualTo(variantId);
        assertThat(orderResponse.getItems().get(0).getProductName()).isEqualTo("Keyboard");
        assertThat(orderResponse.getItems().get(0).getVariantName()).isEqualTo("Black");
        assertThat(orderResponse.getItems().get(0).getUnitPrice()).isEqualTo(125000D);
        assertThat(orderResponse.getItems().get(0).getLineTotal()).isEqualTo(250000D);
        assertThat(orderResponse.getItems().get(0).getSubtotal()).isEqualTo(250000D);
        assertThat(orderResponse.getItems().get(0).getProductImage())
                .isEqualTo("https://cdn.example.com/products/keyboard/main.png");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerId(eq(customerId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void getCustomerOrdersThrowsWhenCustomerDoesNotExist() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsById(customerId)).thenReturn(false);

        assertThatThrownBy(() -> customerManagementService.getCustomerOrders(customerId, 0, 10, "createdAt,desc"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    private Customer createCustomer(String fullName, String email, boolean active, Instant registeredAt) {
        return createCustomer(fullName, email, active, registeredAt, Role.CUSTOMER);
    }

    private Customer createCustomer(String fullName, String email, boolean active, Instant registeredAt, Role role) {
        AccountUser user = AccountUser.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("secret")
                .role(role)
                .isActive(active)
                .createdAt(registeredAt)
                .build();

        return Customer.builder()
                .id(UUID.randomUUID())
                .fullName(fullName)
                .userInfo(user)
                .build();
    }

    private static class AggregateProjection implements CustomerOrderAggregateProjection {
        private final UUID customerId;
        private final long totalOrders;
        private final double totalSpent;
        private final Instant lastOrderAt;

        private AggregateProjection(UUID customerId, long totalOrders, double totalSpent, Instant lastOrderAt) {
            this.customerId = customerId;
            this.totalOrders = totalOrders;
            this.totalSpent = totalSpent;
            this.lastOrderAt = lastOrderAt;
        }

        @Override
        public UUID getCustomerId() {
            return customerId;
        }

        @Override
        public long getTotalOrders() {
            return totalOrders;
        }

        @Override
        public double getTotalSpent() {
            return totalSpent;
        }

        @Override
        public Instant getLastOrderAt() {
            return lastOrderAt;
        }
    }
}

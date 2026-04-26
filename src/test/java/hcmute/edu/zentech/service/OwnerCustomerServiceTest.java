package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.OwnerCustomerMapper;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerCustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    private OwnerCustomerService ownerCustomerService;

    @BeforeEach
    void setUp() {
        ownerCustomerService = new OwnerCustomerService(
                customerRepository,
                orderRepository,
                orderDetailRepository,
                new OwnerCustomerMapper()
        );
    }

    @Test
    void getCustomersReturnsPagedSummariesWithAggregates() {
        Customer firstCustomer = createCustomer("Alice Nguyen", "alice@example.com", true, Instant.parse("2026-04-15T10:15:30Z"));
        Customer secondCustomer = createCustomer("Bob Tran", "bob@example.com", false, Instant.parse("2026-04-14T10:15:30Z"));

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(customerRepository.searchCustomers(eq("alice"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstCustomer, secondCustomer), pageRequest, 2));
        when(orderRepository.findCustomerOrderAggregates(anyList(), eq(OrderStatus.CANCELLED), eq(OrderStatus.COMPLETED), eq(PaymentStatus.REFUNDED), eq(PaymentStatus.SUCCESS)))
                .thenReturn(List.of(new AggregateProjection(firstCustomer.getId(), 3L, 1500000D, Instant.parse("2026-04-16T08:00:00Z"))));

        PageResponse<CustomerSummaryResponse> response = ownerCustomerService.getCustomers(0, 10, "registeredAt,desc", "  alice  ", true);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getCustomerId()).isEqualTo(firstCustomer.getId());
        assertThat(response.getContent().get(0).getTotalOrders()).isEqualTo(3L);
        assertThat(response.getContent().get(0).getTotalSpent()).isEqualTo(1500000D);
        assertThat(response.getContent().get(1).getTotalOrders()).isZero();
        assertThat(response.getContent().get(1).getTotalSpent()).isZero();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).searchCustomers(eq("alice"), eq(true), pageableCaptor.capture());
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

        CustomerDetailResponse response = ownerCustomerService.getCustomerDetail(customerId);

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

        assertThatThrownBy(() -> ownerCustomerService.getCustomerDetail(customerId))
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

        PageResponse<CustomerOrderHistoryResponse> response = ownerCustomerService.getCustomerOrders(customerId, 0, 10, "createdAt,desc");

        assertThat(response.getContent()).hasSize(1);
        CustomerOrderHistoryResponse orderResponse = response.getContent().get(0);
        assertThat(orderResponse.getOrderId()).isEqualTo(orderId);
        assertThat(orderResponse.getItems()).hasSize(1);
        assertThat(orderResponse.getItems().get(0).getProductVariantId()).isEqualTo(variantId);
        assertThat(orderResponse.getItems().get(0).getLineTotal()).isEqualTo(250000D);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerId(eq(customerId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void getCustomerOrdersThrowsWhenCustomerDoesNotExist() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsById(customerId)).thenReturn(false);

        assertThatThrownBy(() -> ownerCustomerService.getCustomerOrders(customerId, 0, 10, "createdAt,desc"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    private Customer createCustomer(String fullName, String email, boolean active, Instant registeredAt) {
        AccountUser user = AccountUser.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("secret")
                .role(Role.CUSTOMER)
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

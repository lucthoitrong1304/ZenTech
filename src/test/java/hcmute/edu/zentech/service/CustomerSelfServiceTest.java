package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CustomerAddressRequest;
import hcmute.edu.zentech.dto.request.UpdateMyProfileRequest;
import hcmute.edu.zentech.dto.response.CustomerAddressResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherResponse;
import hcmute.edu.zentech.dto.response.MyProfileResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.CustomerSelfMapper;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.CouponType;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.CustomerVoucherStatus;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentMethod;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.CustomerVoucherRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSelfServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private CustomerVoucherRepository customerVoucherRepository;

    @Mock
    private R2StorageService r2StorageService;

    private CustomerSelfService customerSelfService;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        authenticate(accountId);
        customerSelfService = new CustomerSelfService(
                customerRepository,
                orderRepository,
                orderDetailRepository,
                customerVoucherRepository,
                r2StorageService,
                new CustomerSelfMapper()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyProfileReturnsCurrentCustomerProfile() {
        Customer customer = createCustomer(accountId);
        when(customerRepository.findByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));

        MyProfileResponse response = customerSelfService.getMyProfile();

        assertThat(response.getCustomerId()).isEqualTo(customer.getId());
        assertThat(response.getFullName()).isEqualTo("Alice Nguyen");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/avatar.jpg");
    }

    @Test
    void updateMyProfileUpdatesNameAndImageButKeepsEmail() {
        Customer customer = createCustomer(accountId);
        when(customerRepository.findByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));

        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setFullName("  Alice Tran  ");
        request.setImageUrl("  https://cdn.example.com/new-avatar.jpg  ");

        MyProfileResponse response = customerSelfService.updateMyProfile(request);

        assertThat(customer.getFullName()).isEqualTo("Alice Tran");
        assertThat(customer.getImageUrl()).isEqualTo("https://cdn.example.com/new-avatar.jpg");
        assertThat(customer.getUserInfo().getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void createMyAddressMakesFirstAddressDefault() {
        Customer customer = createCustomer(accountId);
        customer.setAddressList(new LinkedHashSet<>());
        when(customerRepository.findDetailByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));

        CustomerAddressResponse response = customerSelfService.createMyAddress(addressRequest(false));

        assertThat(response.isDefault()).isTrue();
        assertThat(customer.getAddressList()).hasSize(1);
        assertThat(customer.getAddressList().iterator().next().isDefault()).isTrue();
    }

    @Test
    void setMyDefaultAddressClearsOtherDefaultAddresses() {
        Address first = createAddress(true, Instant.parse("2026-05-01T00:00:00Z"));
        Address second = createAddress(false, Instant.parse("2026-05-02T00:00:00Z"));
        Customer customer = createCustomer(accountId);
        customer.setAddressList(new LinkedHashSet<>(List.of(first, second)));
        when(customerRepository.findDetailByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));

        CustomerAddressResponse response = customerSelfService.setMyDefaultAddress(second.getId());

        assertThat(response.getAddressId()).isEqualTo(second.getId());
        assertThat(response.isDefault()).isTrue();
        assertThat(first.isDefault()).isFalse();
        assertThat(second.isDefault()).isTrue();
    }

    @Test
    void deleteMyAddressSoftDeletesAndHidesAddressFromList() {
        Address address = createAddress(true, Instant.parse("2026-05-01T00:00:00Z"));
        Customer customer = createCustomer(accountId);
        customer.setAddressList(new LinkedHashSet<>(List.of(address)));
        when(customerRepository.findDetailByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));

        customerSelfService.deleteMyAddress(address.getId());
        List<CustomerAddressResponse> addresses = customerSelfService.getMyAddresses();

        assertThat(address.isDeleted()).isTrue();
        assertThat(address.getDeletedAt()).isNotNull();
        assertThat(addresses).isEmpty();
    }

    @Test
    void getMyOrderDetailReturnsOnlyCurrentCustomerOrder() {
        Customer customer = createCustomer(accountId);
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId);
        OrderDetail orderDetail = createOrderDetail(order);

        when(customerRepository.findByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndCustomer_Id(orderId, customer.getId())).thenReturn(Optional.of(order));
        when(orderDetailRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(orderDetail));
        when(r2StorageService.getPresignedGetUrl("products/keyboard/main.png")).thenReturn("https://cdn.example.com/keyboard.png");

        CustomerOrderDetailResponse response = customerSelfService.getMyOrderDetail(orderId);

        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductImage()).isEqualTo("https://cdn.example.com/keyboard.png");
    }

    @Test
    void getMyOrderDetailThrowsWhenOrderDoesNotBelongToCurrentCustomer() {
        Customer customer = createCustomer(accountId);
        UUID orderId = UUID.randomUUID();

        when(customerRepository.findByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndCustomer_Id(orderId, customer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerSelfService.getMyOrderDetail(orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void getMyVouchersReturnsResolvedVoucherStatuses() {
        Customer customer = createCustomer(accountId);
        CustomerVoucher available = createVoucher(activeCoupon(Instant.now().plusSeconds(3600)), null);
        CustomerVoucher used = createVoucher(activeCoupon(Instant.now().plusSeconds(3600)), Instant.now());
        CustomerVoucher expired = createVoucher(inactiveCoupon(), null);

        when(customerRepository.findByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));
        when(customerVoucherRepository.findByCustomer_Id(eq(customer.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(available, used, expired), PageRequest.of(0, 10), 3));

        PageResponse<CustomerVoucherResponse> response = customerSelfService.getMyVouchers(0, 10, "issuedAt,desc", null);

        assertThat(response.getContent()).extracting(CustomerVoucherResponse::getStatus)
                .containsExactly(CustomerVoucherStatus.AVAILABLE, CustomerVoucherStatus.USED, CustomerVoucherStatus.EXPIRED);
    }

    private void authenticate(UUID accountId) {
        CustomUserDetails principal = new CustomUserDetails(
                accountId,
                "alice@example.com",
                "secret",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private Customer createCustomer(UUID accountId) {
        AccountUser user = AccountUser.builder()
                .id(accountId)
                .email("alice@example.com")
                .password("secret")
                .role(Role.CUSTOMER)
                .isActive(true)
                .createdAt(Instant.parse("2026-04-15T10:15:30Z"))
                .build();

        return Customer.builder()
                .id(UUID.randomUUID())
                .fullName("Alice Nguyen")
                .imageUrl("https://cdn.example.com/avatar.jpg")
                .userInfo(user)
                .build();
    }

    private CustomerAddressRequest addressRequest(boolean isDefault) {
        CustomerAddressRequest request = new CustomerAddressRequest();
        request.setPhoneNumber("0909000999");
        request.setProvince("Ho Chi Minh");
        request.setWard("Ward 1");
        request.setStreet("123 Nguyen Hue");
        request.setDefault(isDefault);
        return request;
    }

    private Address createAddress(boolean isDefault, Instant createdAt) {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setPhoneNumber("0909000999");
        address.setProvince("Ho Chi Minh");
        address.setWard("Ward 1");
        address.setStreet("123 Nguyen Hue");
        address.setDefault(isDefault);
        address.setCreatedAt(createdAt);
        address.setUpdatedAt(createdAt);
        return address;
    }

    private Order createOrder(UUID orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setOriginalTotalPrice(250000D);
        order.setDiscountAmount(0D);
        order.setShippingFee(15000D);
        order.setFinalPrice(265000D);
        return order;
    }

    private OrderDetail createOrderDetail(Order order) {
        Product product = new Product();
        product.setProductName("Keyboard");
        product.setRepresentativeImageKey("products/keyboard/main.png");

        ProductVariant variant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .name("Black")
                .product(product)
                .build();

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setId(UUID.randomUUID());
        orderDetail.setOrder(order);
        orderDetail.setProductVariant(variant);
        orderDetail.setQuantity(1);
        orderDetail.setPriceAtPurchase(250000D);
        return orderDetail;
    }

    private CustomerVoucher createVoucher(Coupon coupon, Instant usedAt) {
        CustomerVoucher voucher = new CustomerVoucher();
        voucher.setId(UUID.randomUUID());
        voucher.setCoupon(coupon);
        voucher.setIssuedAt(Instant.parse("2026-05-01T00:00:00Z"));
        voucher.setUsedAt(usedAt);
        return voucher;
    }

    private Coupon activeCoupon(Instant endAt) {
        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID());
        coupon.setCode(UUID.randomUUID().toString());
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setDiscountValue(50000D);
        coupon.setActive(true);
        coupon.setStartAt(Instant.parse("2026-01-01T00:00:00Z"));
        coupon.setEndAt(endAt);
        return coupon;
    }

    private Coupon inactiveCoupon() {
        Coupon coupon = activeCoupon(Instant.now().plusSeconds(3600));
        coupon.setActive(false);
        return coupon;
    }
}

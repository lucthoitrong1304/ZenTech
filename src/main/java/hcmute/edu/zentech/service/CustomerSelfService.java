package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CustomerAddressRequest;
import hcmute.edu.zentech.dto.request.UpdateMyProfileRequest;
import hcmute.edu.zentech.dto.request.ReturnRequestCreateRequest;
import hcmute.edu.zentech.dto.response.CustomerAddressResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherResponse;
import hcmute.edu.zentech.dto.response.MyProfileResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.ReturnRequestResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.CustomerSelfMapper;
import hcmute.edu.zentech.mapper.ReturnRequestMapper;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.CustomerVoucherStatus;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.ReturnRequest;
import hcmute.edu.zentech.model.ReturnRequestStatus;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.CustomerVoucherRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ReturnRequestRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerSelfService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_ORDER_SORT = "createdAt,desc";
    private static final String DEFAULT_VOUCHER_SORT = "issuedAt,desc";

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomerVoucherRepository customerVoucherRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final R2StorageService r2StorageService;
    private final CustomerSelfMapper customerSelfMapper;
    private final ReturnRequestMapper returnRequestMapper;

    public MyProfileResponse getMyProfile() {
        MyProfileResponse response = customerSelfMapper.toMyProfileResponse(getCurrentCustomer());
        if (response.getImageUrl() != null && !response.getImageUrl().startsWith("http")) {
            response.setImageUrl(r2StorageService.getPresignedGetUrl(response.getImageUrl()));
        }
        return response;
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UpdateMyProfileRequest request) {
        Customer customer = getCurrentCustomer();

        if (request.getFullName() != null) {
            customer.setFullName(normalizeRequiredText(request.getFullName(), "fullName"));
        }

        if (request.getImageUrl() != null) {
            customer.setImageUrl(normalizeOptionalText(request.getImageUrl()));
        }

        MyProfileResponse response = customerSelfMapper.toMyProfileResponse(customer);
        if (response.getImageUrl() != null && !response.getImageUrl().startsWith("http")) {
            response.setImageUrl(r2StorageService.getPresignedGetUrl(response.getImageUrl()));
        }
        return response;
    }


    public List<CustomerAddressResponse> getMyAddresses() {
        Customer customer = getCurrentCustomerWithAddresses();

        return getActiveAddresses(customer).stream()
                .sorted(addressComparator())
                .map(customerSelfMapper::toCustomerAddressResponse)
                .toList();
    }

    @Transactional
    public CustomerAddressResponse createMyAddress(CustomerAddressRequest request) {
        Customer customer = getCurrentCustomerWithAddresses();
        List<Address> activeAddresses = getActiveAddresses(customer);

        Address address = new Address();
        applyAddressRequest(address, request);
        address.setCreatedAt(Instant.now());
        address.setUpdatedAt(address.getCreatedAt());
        address.setDeleted(false);

        ensureAddressList(customer).add(address);

        if (activeAddresses.isEmpty() || request.isDefault()) {
            setOnlyDefault(customer, address);
        }

        return customerSelfMapper.toCustomerAddressResponse(address);
    }

    @Transactional
    public CustomerAddressResponse updateMyAddress(UUID addressId, CustomerAddressRequest request) {
        Customer customer = getCurrentCustomerWithAddresses();
        Address address = getOwnedActiveAddress(customer, addressId);

        applyAddressRequest(address, request);
        address.setUpdatedAt(Instant.now());

        if (request.isDefault()) {
            setOnlyDefault(customer, address);
        } else {
            address.setDefault(false);
        }

        return customerSelfMapper.toCustomerAddressResponse(address);
    }

    @Transactional
    public CustomerAddressResponse setMyDefaultAddress(UUID addressId) {
        Customer customer = getCurrentCustomerWithAddresses();
        Address address = getOwnedActiveAddress(customer, addressId);

        setOnlyDefault(customer, address);
        address.setUpdatedAt(Instant.now());

        return customerSelfMapper.toCustomerAddressResponse(address);
    }

    @Transactional
    public void deleteMyAddress(UUID addressId) {
        Customer customer = getCurrentCustomerWithAddresses();
        Address address = getOwnedActiveAddress(customer, addressId);
        boolean wasDefault = address.isDefault();
        Instant now = Instant.now();

        address.setDeleted(true);
        address.setDeletedAt(now);
        address.setUpdatedAt(now);
        address.setDefault(false);

        if (wasDefault) {
            getActiveAddresses(customer).stream()
                    .filter(activeAddress -> activeAddress.getId() != null && !activeAddress.getId().equals(addressId))
                    .min(Comparator.comparing(Address::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .ifPresent(nextDefault -> {
                        nextDefault.setDefault(true);
                        nextDefault.setUpdatedAt(now);
                    });
        }
    }

    public PageResponse<CustomerOrderHistoryResponse> getMyOrders(
            int page,
            int size,
            String sort,
            OrderStatus status
    ) {
        UUID customerId = getCurrentCustomer().getId();
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildOrderSort(sort));

        Page<Order> orderPage = orderRepository.findByCustomerIdAndOptionalStatus(customerId, status, pageable);
        List<OrderDetail> orderDetails = getOrderDetails(orderPage.getContent().stream().map(Order::getId).toList());
        Map<UUID, List<OrderDetail>> orderDetailsMap = groupOrderDetailsByOrderId(orderDetails);
        Map<UUID, String> productImageUrls = getProductImageUrls(orderDetails);

        List<CustomerOrderHistoryResponse> content = orderPage.getContent().stream()
                .map(order -> customerSelfMapper.toCustomerOrderHistoryResponse(
                        order,
                        orderDetailsMap.getOrDefault(order.getId(), List.of()),
                        productImageUrls
                ))
                .toList();

        return PageResponse.from(orderPage, content);
    }

    public CustomerOrderDetailResponse getMyOrderDetail(UUID orderId) {
        UUID customerId = getCurrentCustomer().getId();
        Order order = orderRepository.findByIdAndCustomer_Id(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        List<OrderDetail> orderDetails = getOrderDetails(List.of(orderId));
        Map<UUID, String> productImageUrls = getProductImageUrls(orderDetails);

        return customerSelfMapper.toCustomerOrderDetailResponse(order, orderDetails, productImageUrls);
    }

    public PageResponse<CustomerVoucherResponse> getMyVouchers(
            int page,
            int size,
            String sort,
            CustomerVoucherStatus status
    ) {
        UUID customerId = getCurrentCustomer().getId();
        Instant now = Instant.now();
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildVoucherSort(sort));

        Page<CustomerVoucher> voucherPage = getVoucherPage(customerId, status, now, pageable);

        List<CustomerVoucherResponse> content = voucherPage.getContent().stream()
                .map(voucher -> customerSelfMapper.toCustomerVoucherResponse(voucher, resolveVoucherStatus(voucher, now)))
                .toList();

        return PageResponse.from(voucherPage, content);
    }

    private Page<CustomerVoucher> getVoucherPage(
            UUID customerId,
            CustomerVoucherStatus status,
            Instant now,
            Pageable pageable
    ) {
        if (status == CustomerVoucherStatus.AVAILABLE) {
            return customerVoucherRepository.findAvailableByCustomerId(customerId, now, pageable);
        }

        if (status == CustomerVoucherStatus.USED) {
            return customerVoucherRepository.findByCustomer_IdAndUsedAtIsNotNull(customerId, pageable);
        }

        if (status == CustomerVoucherStatus.EXPIRED) {
            return customerVoucherRepository.findExpiredByCustomerId(customerId, now, pageable);
        }

        return customerVoucherRepository.findByCustomer_Id(customerId, pageable);
    }

    private Customer getCurrentCustomer() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        return customerRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new AccessDeniedException("Only customers can access customer self-service APIs"));
    }

    private Customer getCurrentCustomerWithAddresses() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        return customerRepository.findDetailByUserInfo_Id(accountId)
                .orElseThrow(() -> new AccessDeniedException("Only customers can access customer self-service APIs"));
    }

    private Set<Address> ensureAddressList(Customer customer) {
        if (customer.getAddressList() == null) {
            customer.setAddressList(new LinkedHashSet<>());
        }

        return customer.getAddressList();
    }

    private List<Address> getActiveAddresses(Customer customer) {
        if (customer.getAddressList() == null || customer.getAddressList().isEmpty()) {
            return List.of();
        }

        return customer.getAddressList().stream()
                .filter(address -> !address.isDeleted())
                .toList();
    }

    private Address getOwnedActiveAddress(Customer customer, UUID addressId) {
        return getActiveAddresses(customer).stream()
                .filter(address -> address.getId() != null && address.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
    }

    private void applyAddressRequest(Address address, CustomerAddressRequest request) {
        address.setPhoneNumber(normalizeRequiredText(request.getPhoneNumber(), "phoneNumber"));
        address.setProvince(normalizeRequiredText(request.getProvince(), "province"));
        address.setWard(normalizeRequiredText(request.getWard(), "ward"));
        address.setStreet(normalizeRequiredText(request.getStreet(), "street"));
    }

    private void setOnlyDefault(Customer customer, Address selectedAddress) {
        Instant now = Instant.now();

        getActiveAddresses(customer).forEach(address -> {
            boolean shouldBeDefault = address == selectedAddress
                    || (address.getId() != null && address.getId().equals(selectedAddress.getId()));
            address.setDefault(shouldBeDefault);
            address.setUpdatedAt(now);
        });
    }

    private Comparator<Address> addressComparator() {
        return Comparator.comparing(Address::isDefault).reversed()
                .thenComparing(Address::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
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

    private Map<UUID, String> getProductImageUrls(Collection<OrderDetail> orderDetails) {
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

    private CustomerVoucherStatus resolveVoucherStatus(CustomerVoucher voucher, Instant now) {
        if (voucher.getUsedAt() != null) {
            return CustomerVoucherStatus.USED;
        }

        Coupon coupon = voucher.getCoupon();
        if (coupon == null || !coupon.isActive() || (coupon.getEndAt() != null && coupon.getEndAt().isBefore(now))) {
            return CustomerVoucherStatus.EXPIRED;
        }

        return CustomerVoucherStatus.AVAILABLE;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalizedValue = normalizeOptionalText(value);
        if (normalizedValue == null) {
            throw new RuntimeException(fieldName + " is required");
        }

        return normalizedValue;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
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

    private Sort buildVoucherSort(String sort) {
        return buildSort(sort, DEFAULT_VOUCHER_SORT, Map.of(
                "issuedAt", "issuedAt",
                "usedAt", "usedAt",
                "endAt", "coupon.endAt",
                "couponCode", "coupon.code"
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

    @Transactional
    public ReturnRequestResponse createReturnRequest(UUID orderId, ReturnRequestCreateRequest request) {
        Customer currentCustomer = getCurrentCustomer();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getCustomer().getId().equals(currentCustomer.getId())) {
            throw new AccessDeniedException("You do not have permission to return this order");
        }

        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new RuntimeException("Chỉ đơn hàng đã hoàn thành mới có thể yêu cầu trả hàng.");
        }

        if (returnRequestRepository.findByOrderId(orderId).isPresent()) {
            throw new RuntimeException("Yêu cầu trả hàng cho đơn hàng này đã tồn tại.");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrder(order);
        returnRequest.setReason(request.getReason());
        returnRequest.setDetails(request.getDetails());
        returnRequest.setStatus(ReturnRequestStatus.PENDING);
        returnRequest.setResellable(false);

        // Process proof file keys: move from temp/returns/ to evidence/returns/
        String tempKeys = request.getProofFileKeys();
        String permanentKeys = "";
        if (tempKeys != null && !tempKeys.isBlank()) {
            List<String> movedKeys = new java.util.ArrayList<>();
            for (String key : tempKeys.split(",")) {
                String trimmedKey = key.trim();
                if (trimmedKey.startsWith("temp/returns/")) {
                    String permanentKey = trimmedKey.replace("temp/returns/", "evidence/returns/");
                    r2StorageService.moveObject(trimmedKey, permanentKey);
                    movedKeys.add(permanentKey);
                } else {
                    movedKeys.add(trimmedKey);
                }
            }
            permanentKeys = String.join(",", movedKeys);
        }
        returnRequest.setProofFileKeys(permanentKeys);

        order.setOrderStatus(OrderStatus.RETURN_REQUESTED);
        orderRepository.save(order);

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        return returnRequestMapper.toResponse(saved);
    }

    private record OrderDetailImage(OrderDetail orderDetail, String imageUrl) {
    }
}

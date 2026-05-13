package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.OwnerCustomerMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.projection.CustomerOrderAggregateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerCustomerManagementService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_CUSTOMER_SORT = "registeredAt,desc";
    private static final String DEFAULT_ORDER_SORT = "createdAt,desc";

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final R2StorageService r2StorageService;
    private final OwnerCustomerMapper ownerCustomerMapper;

    public PageResponse<CustomerSummaryResponse> getCustomers(int page, int size, String sort, String keyword, Boolean active) {
        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                buildCustomerSort(sort)
        );

        Page<Customer> customerPage = customerRepository.searchCustomers(normalizeKeyword(keyword), active, Role.CUSTOMER, pageable);
        Map<UUID, CustomerOrderAggregateProjection> aggregateMap = getAggregateMap(
                customerPage.getContent().stream().map(Customer::getId).toList()
        );

        List<CustomerSummaryResponse> content = customerPage.getContent().stream()
                .map(customer -> ownerCustomerMapper.toCustomerSummaryResponse(customer, aggregateMap.get(customer.getId())))
                .toList();

        return PageResponse.from(customerPage, content);
    }

    public CustomerDetailResponse getCustomerDetail(UUID customerId) {
        Customer customer = customerRepository.findDetailById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        CustomerOrderAggregateProjection aggregate = getAggregateMap(List.of(customerId)).get(customerId);
        return ownerCustomerMapper.toCustomerDetailResponse(customer, aggregate);
    }

    @Transactional
    public CustomerDetailResponse updateCustomerStatus(UUID customerId, Boolean active) {
        Customer customer = customerRepository.findDetailById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        if (customer.getUserInfo().getRole() != Role.CUSTOMER) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }

        customer.getUserInfo().setActive(active);
        CustomerOrderAggregateProjection aggregate = getAggregateMap(List.of(customerId)).get(customerId);
        return ownerCustomerMapper.toCustomerDetailResponse(customer, aggregate);
    }

    public PageResponse<CustomerOrderHistoryResponse> getCustomerOrders(UUID customerId, int page, int size, String sort) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }

        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                buildOrderSort(sort)
        );

        Page<Order> orderPage = orderRepository.findByCustomerId(customerId, pageable);
        List<OrderDetail> orderDetails = getOrderDetails(orderPage.getContent().stream().map(Order::getId).toList());
        Map<UUID, List<OrderDetail>> orderDetailsMap = groupOrderDetailsByOrderId(orderDetails);
        Map<UUID, String> productImageUrls = getProductImageUrls(orderDetails);

        List<CustomerOrderHistoryResponse> content = orderPage.getContent().stream()
                .map(order -> ownerCustomerMapper.toCustomerOrderHistoryResponse(
                        order,
                        orderDetailsMap.getOrDefault(order.getId(), List.of()),
                        productImageUrls
                ))
                .toList();

        return PageResponse.from(orderPage, content);
    }

    private Map<UUID, CustomerOrderAggregateProjection> getAggregateMap(List<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return orderRepository.findCustomerOrderAggregates(
                        customerIds,
                        OrderStatus.CANCELLED,
                        OrderStatus.COMPLETED,
                        PaymentStatus.REFUNDED,
                        PaymentStatus.SUCCESS
                ).stream()
                .collect(Collectors.toMap(CustomerOrderAggregateProjection::getCustomerId, Function.identity()));
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
                .map(orderDetail -> new OrderDetailImage(orderDetail, getProductImageUrl(orderDetail.getProductVariant())))
                .filter(detailImage -> detailImage.orderDetail().getId() != null && detailImage.imageUrl() != null)
                .collect(Collectors.toMap(
                        detailImage -> detailImage.orderDetail().getId(),
                        OrderDetailImage::imageUrl,
                        (left, right) -> left
                ));
    }

    private String getProductImageUrl(ProductVariant productVariant) {
        if (productVariant == null || productVariant.getProduct() == null) {
            return null;
        }

        String representativeImageKey = productVariant.getProduct().getRepresentativeImageKey();
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

    private Sort buildCustomerSort(String sort) {
        return buildSort(sort, DEFAULT_CUSTOMER_SORT, Map.of(
                "registeredAt", "userInfo.createdAt",
                "fullName", "fullName",
                "email", "userInfo.email"
        ));
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

        //Thêm trường "id" (mặc định tăng dần) làm tiêu chí sắp xếp thứ 2
        return Sort.by(
                new Sort.Order(direction, mappedField).nullsLast(),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
    }

    private record OrderDetailImage(OrderDetail orderDetail, String imageUrl) {
    }
}

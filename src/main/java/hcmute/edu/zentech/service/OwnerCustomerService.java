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
public class OwnerCustomerService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_CUSTOMER_SORT = "registeredAt,desc";
    private static final String DEFAULT_ORDER_SORT = "createdAt,desc";

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OwnerCustomerMapper ownerCustomerMapper;

    public PageResponse<CustomerSummaryResponse> getCustomers(int page, int size, String sort, String keyword, Boolean active) {
        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                buildCustomerSort(sort)
        );

        Page<Customer> customerPage = customerRepository.searchCustomers(normalizeKeyword(keyword), active, pageable);
        Map<UUID, CustomerOrderAggregateProjection> aggregateMap = getAggregateMap(
                customerPage.getContent().stream().map(Customer::getId).toList()
        );

        List<CustomerSummaryResponse> content = customerPage.getContent().stream()
                .filter(customer -> customer.getUserInfo().getRole() == Role.CUSTOMER)
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
        Map<UUID, List<OrderDetail>> orderDetailsMap = getOrderDetailsMap(
                orderPage.getContent().stream().map(Order::getId).toList()
        );

        List<CustomerOrderHistoryResponse> content = orderPage.getContent().stream()
                .map(order -> ownerCustomerMapper.toCustomerOrderHistoryResponse(
                        order,
                        orderDetailsMap.getOrDefault(order.getId(), List.of())
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

    private Map<UUID, List<OrderDetail>> getOrderDetailsMap(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return orderDetailRepository.findByOrder_IdIn(orderIds).stream()
                .collect(Collectors.groupingBy(orderDetail -> orderDetail.getOrder().getId()));
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

        return Sort.by(new Sort.Order(direction, mappedField).nullsLast());
    }
}

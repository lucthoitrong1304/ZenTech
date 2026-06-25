package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.GlobalSearchItemResponse;
import hcmute.edu.zentech.dto.response.GlobalSearchResponse;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchManagementService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public GlobalSearchResponse search(String keyword) {
        if (keyword == null || keyword.trim().isBlank()) {
            return emptyResponse();
        }

        String searchKeyword = keyword.trim();
        List<GlobalSearchItemResponse> products = hasAuthority(PermissionCode.PRODUCT_VIEW)
                ? searchProducts(searchKeyword)
                : List.of();
        List<GlobalSearchItemResponse> orders = hasAuthority(PermissionCode.ORDER_VIEW)
                ? searchOrders(searchKeyword)
                : List.of();
        List<GlobalSearchItemResponse> customers = hasAuthority(PermissionCode.CUSTOMER_VIEW)
                ? searchCustomers(searchKeyword)
                : List.of();

        return GlobalSearchResponse.builder()
                .products(products)
                .orders(orders)
                .customers(customers)
                .build();
    }

    private GlobalSearchResponse emptyResponse() {
        return GlobalSearchResponse.builder()
                .products(List.of())
                .orders(List.of())
                .customers(List.of())
                .build();
    }

    private List<GlobalSearchItemResponse> searchProducts(String keyword) {
        Page<Product> page = productRepository.searchManagementProducts(
                keyword,
                false,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );
        return page.getContent().stream()
                .map(product -> GlobalSearchItemResponse.builder()
                        .id(product.getId().toString())
                        .icon("Package")
                        .label(product.getProductName())
                        .description("Mã SP: ZT-" + product.getId().toString().substring(0, 8).toUpperCase())
                        .path("/management/products")
                        .build())
                .toList();
    }

    private List<GlobalSearchItemResponse> searchOrders(String keyword) {
        String orderKeyword = keyword.startsWith("#") ? keyword.substring(1).trim() : keyword;
        Page<Order> page = orderRepository.searchManagementOrders(
                orderKeyword,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );
        return page.getContent().stream()
                .map(order -> GlobalSearchItemResponse.builder()
                        .id(order.getId().toString())
                        .icon("ShoppingBag")
                        .label("Đơn hàng #" + order.getId().toString().substring(0, 8).toUpperCase())
                        .description("Trạng thái: " + (order.getOrderStatus() != null
                                ? order.getOrderStatus().toString()
                                : "Chờ xử lý"))
                        .path("/management/orders")
                        .build())
                .toList();
    }

    private List<GlobalSearchItemResponse> searchCustomers(String keyword) {
        Page<Customer> page = customerRepository.searchCustomers(
                keyword,
                null,
                Role.CUSTOMER,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "userInfo.createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );
        return page.getContent().stream()
                .map(customer -> GlobalSearchItemResponse.builder()
                        .id(customer.getId().toString())
                        .icon("Users")
                        .label(customer.getFullName())
                        .description(customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : "")
                        .path("/management/customers")
                        .build())
                .toList();
    }

    private boolean hasAuthority(PermissionCode permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission.name()));
    }
}

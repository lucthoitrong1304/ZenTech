package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.GlobalSearchItemResponse;
import hcmute.edu.zentech.dto.response.GlobalSearchResponse;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
            return GlobalSearchResponse.builder()
                    .products(List.of())
                    .orders(List.of())
                    .customers(List.of())
                    .build();
        }

        String searchKeyword = keyword.trim();

        // 1. Search Products (max 5)
        Page<Product> productPage = productRepository.searchManagementProducts(
                searchKeyword,
                false,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );
        List<GlobalSearchItemResponse> products = productPage.getContent().stream()
                .map(product -> GlobalSearchItemResponse.builder()
                        .id(product.getId().toString())
                        .icon("Package")
                        .label(product.getProductName())
                        .description("Mã SP: ZT-" + product.getId().toString().substring(0, 8).toUpperCase())
                        .path("/management/products")
                        .build())
                .toList();

        // 2. Search Orders (max 5)
        String orderSearchKeyword = searchKeyword;
        if (orderSearchKeyword.startsWith("#")) {
            orderSearchKeyword = orderSearchKeyword.substring(1).trim();
        }
        Page<Order> orderPage = orderRepository.searchManagementOrders(
                orderSearchKeyword,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );
        List<GlobalSearchItemResponse> orders = orderPage.getContent().stream()
                .map(order -> GlobalSearchItemResponse.builder()
                        .id(order.getId().toString())
                        .icon("ShoppingBag")
                        .label("Đơn hàng #" + order.getId().toString().substring(0, 8).toUpperCase())
                        .description("Trạng thái: " + (order.getOrderStatus() != null ? order.getOrderStatus().toString() : "Chờ xử lý"))
                        .path("/management/orders")
                        .build())
                .toList();

        // 3. Search Customers (max 5)
        Page<Customer> customerPage = customerRepository.searchCustomers(
                searchKeyword,
                null,
                Role.CUSTOMER,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "userInfo.createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );
        List<GlobalSearchItemResponse> customers = customerPage.getContent().stream()
                .map(customer -> GlobalSearchItemResponse.builder()
                        .id(customer.getId().toString())
                        .icon("Users")
                        .label(customer.getFullName())
                        .description(customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : "")
                        .path("/management/customers")
                        .build())
                .toList();

        return GlobalSearchResponse.builder()
                .products(products)
                .orders(orders)
                .customers(customers)
                .build();
    }
}

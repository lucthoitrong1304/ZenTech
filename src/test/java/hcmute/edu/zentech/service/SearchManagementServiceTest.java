package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.GlobalSearchResponse;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchManagementServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchOnlyQueriesModulesGrantedToCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "employee",
                        null,
                        List.of(new SimpleGrantedAuthority("PRODUCT_VIEW"))
                )
        );
        when(productRepository.searchManagementProducts(eq("laptop"), eq(false), any()))
                .thenReturn(Page.empty());

        SearchManagementService service = new SearchManagementService(
                productRepository,
                orderRepository,
                customerRepository
        );

        GlobalSearchResponse result = service.search("laptop");

        assertThat(result.getProducts()).isEmpty();
        assertThat(result.getOrders()).isEmpty();
        assertThat(result.getCustomers()).isEmpty();
        verify(productRepository).searchManagementProducts(eq("laptop"), eq(false), any());
        verify(orderRepository, never()).searchManagementOrders(any(), any(), any(), any(), any(), any());
        verify(customerRepository, never()).searchCustomers(any(), any(), any(), any());
    }

    @Test
    void blankKeywordDoesNotQueryAnyRepository() {
        SearchManagementService service = new SearchManagementService(
                productRepository,
                orderRepository,
                customerRepository
        );

        GlobalSearchResponse result = service.search("  ");

        assertThat(result.getProducts()).isEmpty();
        assertThat(result.getOrders()).isEmpty();
        assertThat(result.getCustomers()).isEmpty();
        verify(productRepository, never()).searchManagementProducts(any(), anyBoolean(), any());
        verify(orderRepository, never()).searchManagementOrders(any(), any(), any(), any(), any(), any());
        verify(customerRepository, never()).searchCustomers(any(), any(), any(), any());
    }
}

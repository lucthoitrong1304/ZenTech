package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.OrderCreateRequest;
import hcmute.edu.zentech.dto.request.OrderUpdateRequest;
import hcmute.edu.zentech.dto.response.OrderManagementDetailResponse;
import hcmute.edu.zentech.dto.response.OrderManagementSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentMethod;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.OrderManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderManagementController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class OrderManagementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderManagementService orderManagementService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getOrdersReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/management/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrdersReturnsForbiddenForCustomerAndEmployeeRoles() throws Exception {
        mockMvc.perform(get("/api/management/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/management/orders").with(user("employee").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrdersAllowsOwnerManagerAndAdminRoles() throws Exception {
        given(orderManagementService.getOrders(anyInt(), anyInt(), anyString(), any(), any(), any()))
                .willReturn(PageResponse.<OrderManagementSummaryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/management/orders").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/management/orders").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/management/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getOrderDetailAllowsManagerRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderManagementService.getOrderDetail(orderId)).willReturn(detailResponse(orderId, OrderStatus.CREATED));

        mockMvc.perform(get("/api/management/orders/{orderId}", orderId).with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }

    @Test
    void createOrderAllowsManagerRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderManagementService.createOrder(any(OrderCreateRequest.class)))
                .willReturn(detailResponse(orderId, OrderStatus.CREATED));

        mockMvc.perform(post("/api/management/orders")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "11111111-1111-1111-1111-111111111111",
                                  "addressId": "22222222-2222-2222-2222-222222222222",
                                  "items": [
                                    {
                                      "productVariantId": "33333333-3333-3333-3333-333333333333",
                                      "quantity": 2
                                    }
                                  ],
                                  "shippingFee": 15000,
                                  "paymentMethod": "CASH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("CREATED"));
    }

    @Test
    void createOrderReturnsBadRequestWhenRequiredFieldIsMissing() throws Exception {
        mockMvc.perform(post("/api/management/orders")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressId": "22222222-2222-2222-2222-222222222222",
                                  "items": [],
                                  "shippingFee": 15000,
                                  "paymentMethod": "CASH"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateOrderAllowsOwnerRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderManagementService.updateOrder(any(UUID.class), any(OrderUpdateRequest.class)))
                .willReturn(detailResponse(orderId, OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/management/orders/{orderId}", orderId)
                        .with(user("owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderStatus": "CONFIRMED",
                                  "paymentStatus": "SUCCESS",
                                  "shippingFee": 20000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));
    }

    @Test
    void deleteOrderAllowsAdminRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderManagementService.cancelOrder(orderId))
                .willReturn(detailResponse(orderId, OrderStatus.CANCELLED));

        mockMvc.perform(delete("/api/management/orders/{orderId}", orderId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
    }

    @Test
    void mutatingRequestsReturnForbiddenForCustomerRole() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/management/orders")
                        .with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/management/orders/{orderId}", orderId)
                        .with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/management/orders/{orderId}", orderId)
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    private OrderManagementDetailResponse detailResponse(UUID orderId, OrderStatus orderStatus) {
        return OrderManagementDetailResponse.builder()
                .orderId(orderId)
                .createdAt(Instant.parse("2026-04-15T10:15:30Z"))
                .orderStatus(orderStatus)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.CASH)
                .originalTotalPrice(200000D)
                .discountAmount(0D)
                .shippingFee(15000D)
                .finalPrice(215000D)
                .items(List.of())
                .coupons(List.of())
                .build();
    }
}

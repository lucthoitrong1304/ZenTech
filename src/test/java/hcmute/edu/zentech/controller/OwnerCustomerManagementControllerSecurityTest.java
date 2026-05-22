package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.CustomerDetailResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.CustomerManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OwnerCustomerManagementController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class OwnerCustomerManagementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerManagementService customerManagementService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getCustomersReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/owner/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomersReturnsForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(get("/api/owner/customers").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCustomersAllowsOwnerRole() throws Exception {
        given(customerManagementService.getCustomers(anyInt(), anyInt(), anyString(), any(), any()))
                .willReturn(PageResponse.<CustomerSummaryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/owner/customers").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCustomersAllowsAdminRole() throws Exception {
        given(customerManagementService.getCustomers(anyInt(), anyInt(), anyString(), any(), any()))
                .willReturn(PageResponse.<CustomerSummaryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/owner/customers").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateCustomerStatusReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(patch("/api/owner/customers/{customerId}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCustomerStatusReturnsForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/owner/customers/{customerId}/status", UUID.randomUUID())
                        .with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCustomerStatusAllowsOwnerRole() throws Exception {
        UUID customerId = UUID.randomUUID();
        given(customerManagementService.updateCustomerStatus(any(UUID.class), anyBoolean()))
                .willReturn(CustomerDetailResponse.builder()
                        .customerId(customerId)
                        .active(false)
                        .build());

        mockMvc.perform(patch("/api/owner/customers/{customerId}/status", customerId)
                        .with(user("owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void updateCustomerStatusAllowsAdminRole() throws Exception {
        UUID customerId = UUID.randomUUID();
        given(customerManagementService.updateCustomerStatus(any(UUID.class), anyBoolean()))
                .willReturn(CustomerDetailResponse.builder()
                        .customerId(customerId)
                        .active(true)
                        .build());

        mockMvc.perform(patch("/api/owner/customers/{customerId}/status", customerId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void updateCustomerStatusReturnsBadRequestWhenActiveIsMissing() throws Exception {
        mockMvc.perform(patch("/api/owner/customers/{customerId}/status", UUID.randomUUID())
                        .with(user("owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("active: active is required"));
    }
}

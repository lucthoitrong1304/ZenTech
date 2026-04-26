package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.CustomerSummaryResponse;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.OwnerCustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OwnerCustomerController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class OwnerCustomerControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OwnerCustomerService ownerCustomerService;

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
        given(ownerCustomerService.getCustomers(anyInt(), anyInt(), anyString(), any(), any()))
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
        given(ownerCustomerService.getCustomers(anyInt(), anyInt(), anyString(), any(), any()))
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
}

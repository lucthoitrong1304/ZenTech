package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.MyProfileResponse;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.CustomerSelfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomerSelfController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class CustomerSelfControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerSelfService customerSelfService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getMyProfileReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/customers/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfileReturnsForbiddenForEmployeeRole() throws Exception {
        mockMvc.perform(get("/api/customers/me/profile").with(user("employee").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyProfileAllowsCustomerRole() throws Exception {
        given(customerSelfService.getMyProfile()).willReturn(MyProfileResponse.builder()
                .customerId(UUID.randomUUID())
                .fullName("Alice Nguyen")
                .email("alice@example.com")
                .imageUrl("https://cdn.example.com/avatar.jpg")
                .registeredAt(Instant.parse("2026-04-15T10:15:30Z"))
                .build());

        mockMvc.perform(get("/api/customers/me/profile").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void createMyAddressReturnsBadRequestWhenPhoneNumberIsMissing() throws Exception {
        mockMvc.perform(post("/api/customers/me/addresses")
                        .with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "province": "Ho Chi Minh",
                                  "ward": "Ward 1",
                                  "street": "123 Nguyen Hue",
                                  "isDefault": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("phoneNumber: phoneNumber is required"));
    }
}

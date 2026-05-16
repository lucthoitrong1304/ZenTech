package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.EmployeeCreateRequest;
import hcmute.edu.zentech.dto.response.EmployeeSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.OwnerEmployeeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OwnerEmployeeController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class OwnerEmployeeControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OwnerEmployeeService ownerEmployeeService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getEmployeesReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/owner/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEmployeesReturnsForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(get("/api/owner/employees").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEmployeesAllowsOwnerManagerAndAdminRoles() throws Exception {
        given(ownerEmployeeService.getEmployees(anyInt(), anyInt(), anyString(), any(), any(), any()))
                .willReturn(PageResponse.<EmployeeSummaryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/owner/employees").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/owner/employees").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/owner/employees").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createEmployeeReturnsForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(post("/api/owner/employees")
                        .with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "employee@example.com",
                                  "fullName": "Employee One",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEmployeeAllowsManagerRole() throws Exception {
        given(ownerEmployeeService.createEmployee(any(EmployeeCreateRequest.class)))
                .willReturn(EmployeeSummaryResponse.builder()
                        .employeeId(UUID.randomUUID())
                        .accountId(UUID.randomUUID())
                        .email("employee@example.com")
                        .fullName("Employee One")
                        .role(Role.EMPLOYEE)
                        .active(false)
                        .createdAt(Instant.parse("2026-04-30T00:00:00Z"))
                        .build());

        mockMvc.perform(post("/api/owner/employees")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "employee@example.com",
                                  "fullName": "Employee One",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("employee@example.com"))
                .andExpect(jsonPath("$.data.active").value(false));
    }
}

package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.ProductCreateRequest;
import hcmute.edu.zentech.dto.request.ProductGroupCreateRequest;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.ProductGroupResponse;
import hcmute.edu.zentech.dto.response.ProductManagementDetailResponse;
import hcmute.edu.zentech.dto.response.ProductManagementSummaryResponse;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.ProductGroupService;
import hcmute.edu.zentech.service.ProductManagementService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductManagementController.class, ProductGroupManagementController.class})
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class ProductManagementControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductManagementService productManagementService;

    @MockBean
    private ProductGroupService productGroupService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void managementProductsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/management/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managementProductsForbidCustomerRole() throws Exception {
        mockMvc.perform(get("/api/management/products").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void managementProductsAllowOwnerManagerAndAdminRoles() throws Exception {
        given(productManagementService.getProducts(anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .willReturn(PageResponse.<ProductManagementSummaryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/management/products").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/management/products").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/management/products").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createProductAllowsManagerRole() throws Exception {
        UUID productId = UUID.randomUUID();
        given(productManagementService.createProduct(any(ProductCreateRequest.class)))
                .willReturn(ProductManagementDetailResponse.builder()
                        .id(productId)
                        .productName("Alpha65")
                        .build());

        mockMvc.perform(post("/api/management/products")
                        .with(user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "Alpha65",
                                  "categoryIds": ["11111111-1111-1111-1111-111111111111"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId.toString()));
    }

    @Test
    void managementProductGroupsFollowSameRolePolicy() throws Exception {
        given(productGroupService.getGroups(anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .willReturn(PageResponse.<ProductGroupResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/management/product-groups").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/management/product-groups").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createProductGroupAllowsAdminRole() throws Exception {
        UUID groupId = UUID.randomUUID();
        given(productGroupService.createGroup(any(ProductGroupCreateRequest.class)))
                .willReturn(ProductGroupResponse.builder()
                        .id(groupId)
                        .groupName("Alpha65")
                        .build());

        mockMvc.perform(post("/api/management/product-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupName\":\"Alpha65\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(groupId.toString()));
    }
}

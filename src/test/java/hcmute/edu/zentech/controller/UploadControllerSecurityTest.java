package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.UploadPresignResponse;
import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.SecurityConfig;
import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import hcmute.edu.zentech.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UploadController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class})
class UploadControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadService uploadService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void createPresignedUploadUrlReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/uploads/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPresignedUploadUrlAllowsAuthenticatedUser() throws Exception {
        given(uploadService.createPresignedUploadUrl(any())).willReturn(UploadPresignResponse.builder()
                .presignedUrl("https://example.com/upload")
                .fileKey("uploads/reviews/user/image.jpg")
                .method("PUT")
                .expiresInMinutes(15L)
                .requiredHeaders(Map.of("Content-Type", "image/jpeg"))
                .build());

        mockMvc.perform(post("/api/uploads/presign")
                        .with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrl").value("https://example.com/upload"))
                .andExpect(jsonPath("$.fileKey").value("uploads/reviews/user/image.jpg"))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.expiresInMinutes").value(15));
    }

    private String validRequestJson() {
        return """
                {
                  "originalFilename": "review.jpg",
                  "contentType": "image/jpeg",
                  "fileSize": 1024,
                  "purpose": "PRODUCT_REVIEW"
                }
                """;
    }
}

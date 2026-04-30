package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ProductReviewRequest;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.repository.ProductReviewRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductReviewRepository productReviewRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private R2StorageService r2StorageService;

    private ProductReviewService productReviewService;
    private UUID currentUserId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        productReviewService = new ProductReviewService(
                productRepository,
                productReviewRepository,
                customerRepository,
                new ProductMapper(),
                r2StorageService
        );

        currentUserId = UUID.randomUUID();
        customer = Customer.builder()
                .id(UUID.randomUUID())
                .fullName("Alice Nguyen")
                .userInfo(AccountUser.builder()
                        .id(currentUserId)
                        .email("alice@example.com")
                        .password("secret")
                        .role(Role.CUSTOMER)
                        .isActive(true)
                        .build())
                .build();

        CustomUserDetails userDetails = CustomUserDetails.build(customer.getUserInfo());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReviewVerifiesImageKeysBeforeSaving() {
        UUID productId = UUID.randomUUID();
        String fileKey = "uploads/reviews/" + currentUserId + "/image.jpg";
        ProductReviewRequest request = new ProductReviewRequest();
        request.setRating(5);
        request.setComment("Great");
        request.setImageKeys(List.of(fileKey));

        when(productRepository.findById(productId)).thenReturn(Optional.of(Product.builder()
                .id(productId)
                .productName("Keyboard")
                .build()));
        when(customerRepository.findByUserInfo_Id(currentUserId)).thenReturn(Optional.of(customer));
        when(productReviewRepository.save(any(ProductReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(r2StorageService.getPresignedGetUrl(fileKey)).thenReturn("https://example.com/image.jpg");

        productReviewService.createReview(productId, request);

        verify(r2StorageService).validateUploadedReviewImage(fileKey, currentUserId);
        ArgumentCaptor<ProductReview> reviewCaptor = ArgumentCaptor.forClass(ProductReview.class);
        verify(productReviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getImageKeys()).containsExactly(fileKey);
    }

    @Test
    void createReviewRejectsMoreThanFiveImages() {
        UUID productId = UUID.randomUUID();
        ProductReviewRequest request = new ProductReviewRequest();
        request.setRating(5);
        request.setImageKeys(List.of("1", "2", "3", "4", "5", "6"));

        when(productRepository.findById(productId)).thenReturn(Optional.of(Product.builder()
                .id(productId)
                .productName("Keyboard")
                .build()));
        when(customerRepository.findByUserInfo_Id(currentUserId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> productReviewService.createReview(productId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 5 images");

        verify(r2StorageService, never()).validateUploadedReviewImage(any(), any());
        verify(productReviewRepository, never()).save(any(ProductReview.class));
    }

    @Test
    void createReviewRejectsInvalidR2Key() {
        UUID productId = UUID.randomUUID();
        String fileKey = "uploads/reviews/" + currentUserId + "/missing.jpg";
        ProductReviewRequest request = new ProductReviewRequest();
        request.setRating(5);
        request.setImageKeys(List.of(fileKey));

        when(productRepository.findById(productId)).thenReturn(Optional.of(Product.builder()
                .id(productId)
                .productName("Keyboard")
                .build()));
        when(customerRepository.findByUserInfo_Id(currentUserId)).thenReturn(Optional.of(customer));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Uploaded image does not exist"))
                .when(r2StorageService).validateUploadedReviewImage(fileKey, currentUserId);

        assertThatThrownBy(() -> productReviewService.createReview(productId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");

        verify(productReviewRepository, never()).save(any(ProductReview.class));
    }
}

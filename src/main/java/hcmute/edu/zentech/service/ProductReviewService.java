package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ProductReviewListQueryRequest;
import hcmute.edu.zentech.dto.request.ProductReviewRequest;
import hcmute.edu.zentech.dto.response.PagedResponse;
import hcmute.edu.zentech.dto.response.ProductReviewItemResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.repository.ProductReviewRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductReviewService {
    private static final int MAX_REVIEW_IMAGES = 5;

    private final ProductRepository productRepository;
    private final ProductReviewRepository productReviewRepository;
    private final CustomerRepository customerRepository;
    private final ProductMapper productMapper;
    private final R2StorageService r2StorageService;

    @Transactional(readOnly = true)
    public PagedResponse<ProductReviewItemResponse> getProductReviews(
            UUID productId,
            ProductReviewListQueryRequest request) {
        // 1. check xem sản phẩm review có tồn tại?
        ensureProductExists(productId);

        // 2. Lấy thông tin người gửi reivew
        UUID currentUserId = SecurityContextUtils.getCurrentUserId();
        // 3. Khởi tạo page request và tìm danh sách review của sản phẩm đó.
        PageRequest pageRequest = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        Page<ProductReview> reviewPage = productReviewRepository.findByProduct_Id(productId, pageRequest);

        return PagedResponse.<ProductReviewItemResponse>builder()
                .items(reviewPage.getContent().stream()
                        .map(review -> toReviewItemResponse(review, currentUserId))
                        .toList())
                .page(reviewPage.getNumber())
                .size(reviewPage.getSize())
                .totalItems(reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .hasNext(reviewPage.hasNext())
                .hasPrevious(reviewPage.hasPrevious())
                .build();
    }

    // Hiện tại đang thiếu logic set ảnh
    @Transactional
    public ProductReviewItemResponse createReview(UUID productId, ProductReviewRequest request) {
        Product product = productRepository.findProductDetailById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "ID", productId));

        Customer customer = getCurrentCustomer();

        List<String> imageKeys = validateReviewImageKeys(request.getImageKeys(), customer.getUserInfo().getId());
        String videoKey = validateReviewVideoKey(request.getVideoKey(), customer.getUserInfo().getId());

        ProductReview review = ProductReview.builder()
                .product(product)
                .customer(customer)
                .rating(request.getRating())
                .comment(normalizeComment(request.getComment()))
                .imageKeys(new ArrayList<>(imageKeys))
                .videoKey(videoKey)
                .build();

        ProductReview savedReview = productReviewRepository.save(review);
        return toReviewItemResponse(savedReview, customer.getUserInfo().getId());
    }

    // Hiện tại đang thiếu logic set ảnh
    @Transactional
    public ProductReviewItemResponse updateReview(UUID productId, UUID reviewId, ProductReviewRequest request) {
        Customer customer = getCurrentCustomer();
        // Tìm review của người dùng đó.
        ProductReview review = productReviewRepository.findByIdAndProduct_Id(reviewId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Review", "ID", reviewId));

        ensureOwner(review, customer.getId());
        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));

        if (request.getImageKeys() != null) {
            List<String> imageKeys = validateReviewImageKeys(request.getImageKeys(), customer.getUserInfo().getId());
            review.setImageKeys(new ArrayList<>(imageKeys));
        }

        if (request.getVideoKey() != null) {
            String videoKey = validateReviewVideoKey(request.getVideoKey(), customer.getUserInfo().getId());
            review.setVideoKey(videoKey);
        }

        ProductReview updatedReview = productReviewRepository.save(review);
        return toReviewItemResponse(updatedReview, customer.getUserInfo().getId());
    }

    @Transactional
    public void deleteReview(UUID productId, UUID reviewId) {
        Customer customer = getCurrentCustomer();
        ProductReview review = productReviewRepository.findByIdAndProduct_Id(reviewId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Review", "ID", reviewId));

        ensureOwner(review, customer.getId());
        productReviewRepository.delete(review);
    }

    // Check xem sản phẩm có tồn tại
    private void ensureProductExists(UUID productId) {
        if (!productRepository.existsByIdAndDeletedFalse(productId)) {
            throw new ResourceNotFoundException("Product", "ID", productId);
        }
    }

    // Lấy User đăng nhập hiện tại.
    private Customer getCurrentCustomer() {
        UUID currentUserId = SecurityContextUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        return customerRepository.findByUserInfo_Id(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Only customers can manage reviews")); // Define lại nội dung phản hồi
    }

    // Check xem người dùng có phải là chủ của review đó không
    private void ensureOwner(ProductReview review, UUID customerId) {
        if (review.getCustomer() == null || review.getCustomer().getId() == null) {
            throw new AccessDeniedException("Review owner is invalid"); // Define lại nội dung
        }

        if (!review.getCustomer().getId().equals(customerId)) {
            throw new AccessDeniedException("You can only modify your own reviews");
        }
    }

    // Chuẩn hoá comment
    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }

        String trimmedComment = comment.trim();
        return trimmedComment.isEmpty() ? null : trimmedComment;
    }

    // Validate video key before saving to the review.
    private String validateReviewVideoKey(String videoKey, UUID currentUserId) {
        if (videoKey == null || videoKey.isBlank()) {
            return null;
        }

        r2StorageService.validateUploadedReviewVideo(videoKey, currentUserId);
        return videoKey;
    }

    // Validate image keys before saving them to the review.
    private List<String> validateReviewImageKeys(List<String> imageKeys, UUID currentUserId) {
        if (imageKeys == null) {
            return List.of();
        }

        if (imageKeys.size() > MAX_REVIEW_IMAGES) {
            throw new IllegalArgumentException("A review can contain at most 5 images");
        }

        imageKeys.forEach(fileKey -> r2StorageService.validateUploadedReviewImage(fileKey, currentUserId));
        return imageKeys;
    }

    private ProductReviewItemResponse toReviewItemResponse(ProductReview review, UUID currentUserId) {
        boolean isOwner = currentUserId != null
                && review.getCustomer() != null
                && review.getCustomer().getUserInfo() != null
                && currentUserId.equals(review.getCustomer().getUserInfo().getId());

        String customerName = review.getCustomer() != null ? review.getCustomer().getFullName() : null;

        List<String> presignedUrls = review.getImageKeys() == null ? List.of() :
                review.getImageKeys().stream()
                        .map(r2StorageService::getPresignedGetUrl)
                        .filter(java.util.Objects::nonNull)
                        .toList();

        String videoUrl = review.getVideoKey() != null
                ? r2StorageService.getPresignedGetUrl(review.getVideoKey())
                : null;

        return productMapper.toProductReviewItemResponse(review, customerName, isOwner, presignedUrls, videoUrl);
    }
}

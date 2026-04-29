package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.ProductReviewListQueryRequest;
import hcmute.edu.zentech.dto.request.ProductReviewRequest;
import hcmute.edu.zentech.dto.response.PagedResponse;
import hcmute.edu.zentech.dto.response.ProductDetailResponse;
import hcmute.edu.zentech.dto.response.ProductReviewItemResponse;
import hcmute.edu.zentech.service.ProductReviewService;
import hcmute.edu.zentech.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductReviewService productReviewService;

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable UUID productId) {
        return ResponseEntity.ok(productService.getProductDetail(productId));
    }

    @GetMapping("/{productId}/reviews")
    public ResponseEntity<PagedResponse<ProductReviewItemResponse>> getProductReviews(
            @PathVariable UUID productId,
            @Valid @ModelAttribute ProductReviewListQueryRequest request
    ) {
        return ResponseEntity.ok(productReviewService.getProductReviews(productId, request));
    }

    @PostMapping("/{productId}/reviews")
    public ResponseEntity<ProductReviewItemResponse> createReview(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductReviewRequest request
    ) {
        return ResponseEntity.ok(productReviewService.createReview(productId, request));
    }

    @PutMapping("/{productId}/reviews/{reviewId}")
    public ResponseEntity<ProductReviewItemResponse> updateReview(
            @PathVariable UUID productId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ProductReviewRequest request
    ) {
        return ResponseEntity.ok(productReviewService.updateReview(productId, reviewId, request));
    }

    @DeleteMapping("/{productId}/reviews/{reviewId}")
    public ResponseEntity<String> deleteReview(
            @PathVariable UUID productId,
            @PathVariable UUID reviewId
    ) {
        productReviewService.deleteReview(productId, reviewId);
        return ResponseEntity.ok("Review deleted successfully");
    }
}

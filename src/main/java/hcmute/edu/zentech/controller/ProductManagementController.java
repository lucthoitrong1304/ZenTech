package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.ProductCreateRequest;
import hcmute.edu.zentech.dto.request.ProductUpdateRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.ProductManagementDetailResponse;
import hcmute.edu.zentech.dto.response.ProductManagementSummaryResponse;
import hcmute.edu.zentech.service.ProductManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/products")
@RequiredArgsConstructor
public class ProductManagementController {
    private final ProductManagementService productManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductManagementSummaryResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                productManagementService.getProducts(page, size, sort, keyword, includeDeleted)
        ));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductManagementDetailResponse>> getProductDetail(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(productManagementService.getProductDetail(productId)));
    }

    @PostMapping
    @TrackActivity(action = ActivityAction.CREATE_PRODUCT, area = ActivityArea.MANAGEMENT, module = "PRODUCT", targetType = "PRODUCT", severity = ActivitySeverity.IMPORTANT, summary = "Tạo sản phẩm")
    public ResponseEntity<ApiResponse<ProductManagementDetailResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productManagementService.createProduct(request)));
    }

    @PatchMapping("/{productId}")
    @TrackActivity(action = ActivityAction.UPDATE_PRODUCT, area = ActivityArea.MANAGEMENT, module = "PRODUCT", targetType = "PRODUCT", severity = ActivitySeverity.IMPORTANT, summary = "Cập nhật sản phẩm")
    public ResponseEntity<ApiResponse<ProductManagementDetailResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productManagementService.updateProduct(productId, request)));
    }

    @DeleteMapping("/{productId}")
    @TrackActivity(action = ActivityAction.DELETE_PRODUCT, area = ActivityArea.MANAGEMENT, module = "PRODUCT", targetType = "PRODUCT", severity = ActivitySeverity.CRITICAL, summary = "Xóa sản phẩm")
    public ResponseEntity<ApiResponse<ProductManagementDetailResponse>> deleteProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success(productManagementService.deleteProduct(productId)));
    }
}

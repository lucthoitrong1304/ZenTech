package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.CategoryManagementRequest;
import hcmute.edu.zentech.dto.request.CategoryReorderRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/categories")
@RequiredArgsConstructor
public class CategoryManagementController {
    private final ProductCategoryService productCategoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ResponseEntity<ApiResponse<List<ProductCategorySummaryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.getAllManagementCategories()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<ProductCategorySummaryResponse>> createCategory(
            @Valid @RequestBody CategoryManagementRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.createManagementCategory(request)));
    }

    @PatchMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<ApiResponse<ProductCategorySummaryResponse>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryManagementRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.updateManagementCategory(categoryId, request)));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<ApiResponse<ProductCategorySummaryResponse>> deleteCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.deleteManagementCategory(categoryId)));
    }

    @PatchMapping("/tree")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<ApiResponse<List<ProductCategorySummaryResponse>>> reorderCategories(
            @Valid @RequestBody CategoryReorderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productCategoryService.reorderManagementCategories(request)));
    }
}

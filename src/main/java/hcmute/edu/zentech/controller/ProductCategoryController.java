package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.CategoryProductListQueryRequest;
import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.PagedResponse;
import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ProductCategoryController {
    private final ProductCategoryService productCategoryService;

    @GetMapping
    public ResponseEntity<List<ProductCategorySummaryResponse>> getAllCategories() {
        return ResponseEntity.ok(productCategoryService.getAllCategories());
    }

    @GetMapping("/{categoryId}/products")
    public ResponseEntity<PagedResponse<CategoryProductListItemResponse>> getProductsByCategoryId(
            @PathVariable UUID categoryId,
            @Valid @ModelAttribute CategoryProductListQueryRequest request) {
        return ResponseEntity.ok(productCategoryService.getProductsByCategoryId(categoryId, request));
    }
}

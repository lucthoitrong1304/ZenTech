package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.dto.response.ProductDetailResponse;
import hcmute.edu.zentech.dto.response.ProductReviewItemResponse;
import hcmute.edu.zentech.dto.response.ProductVariantDetailResponse;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {
    public CategoryProductListItemResponse toCategoryProductListItemResponse(
            Product product,
            List<String> imageUrls,
            Double originalPrice,
            Double salePrice,
            Double averageRating) {
        return CategoryProductListItemResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .imageUrls(imageUrls)
                .originalPrice(originalPrice)
                .salePrice(salePrice)
                .averageRating(averageRating)
                .build();
    }

    public ProductCategorySummaryResponse toProductCategorySummaryResponse(ProductCategory category) {
        return ProductCategorySummaryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .shortName(category.getShortName())
                .build();
    }

    // Mapper biến thể sản phẩm
    public ProductVariantDetailResponse toProductVariantDetailResponse(ProductVariant variant, List<String> imageUrls) {
        return ProductVariantDetailResponse.builder()
                .id(variant.getId())
                .originalPrice(variant.getOriginalPrice())
                .salePrice(variant.getSalePrice())
                .name(variant.getName())
                .nameColor(variant.getNameColor())
                .colorCode(variant.getColorCode())
                .saleStartAt(variant.getSaleStartAt())
                .saleEndAt(variant.getSaleEndAt())
                .stockQuantity(variant.getStockQuantity())
                .imageUrls(imageUrls)
                .build();
    }

    // Mapper detail của 1 sản phẩm
    public ProductDetailResponse toProductDetailResponse(
            Product product,
            List<String> productImageUrls,
            List<ProductVariantDetailResponse> variants,
            Double averageRating,
            long totalReviews,
            List<CategoryProductListItemResponse> similarProducts) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .specifications(product.getSpecifications())
                .compatibility(product.getCompatibility())
                .boxContents(product.getBoxContents())
                .supportInfo(product.getSupportInfo())
                .createdAt(product.getCreatedAt())
                .productImageUrls(productImageUrls)
                .similarProducts(similarProducts)
                .variants(variants)
                .averageRating(averageRating)
                .totalReviews(totalReviews)
                .build();
    }

    // Mapper 1 item review
    public ProductReviewItemResponse toProductReviewItemResponse(
            ProductReview review,
            String customerName,
            boolean isOwner) {
        return ProductReviewItemResponse.builder()
                .reviewId(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .customerId(review.getCustomer().getId())
                .customerName(customerName)
                .isOwner(isOwner)
                .build();
    }
}

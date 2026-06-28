package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.dto.response.ProductDetailResponse;
import hcmute.edu.zentech.dto.response.ProductGroupItemResponse;
import hcmute.edu.zentech.dto.response.ProductGroupResponse;
import hcmute.edu.zentech.dto.response.ProductManagementDetailResponse;
import hcmute.edu.zentech.dto.response.ProductManagementSummaryResponse;
import hcmute.edu.zentech.dto.response.ProductReviewItemResponse;
import hcmute.edu.zentech.dto.response.ProductVariantDetailResponse;
import hcmute.edu.zentech.dto.response.ProductVariantManagementResponse;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ProductMapper {
    public CategoryProductListItemResponse toCategoryProductListItemResponse(
            Product product,
            String imageUrl,
            Double originalPrice,
            Double salePrice,
            Double averageRating,
            Integer stockQuantity) {
        return CategoryProductListItemResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .imageUrl(imageUrl)
                .originalPrice(originalPrice)
                .salePrice(salePrice)
                .averageRating(averageRating)
                .stockQuantity(stockQuantity)
                .build();
    }

    public ProductCategorySummaryResponse toProductCategorySummaryResponse(
            ProductCategory category,
            boolean hasChildren,
            List<ProductCategorySummaryResponse> children) {
        return ProductCategorySummaryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .shortName(category.getShortName())
                .visible(category.isVisible())
                .hasChildren(hasChildren)
                .children(children)
                .build();
    }

    // Mapper biến thể sản phẩm
    public ProductVariantDetailResponse toProductVariantDetailResponse(ProductVariant variant) {
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
                .build();
    }

    public ProductVariantManagementResponse toProductVariantManagementResponse(ProductVariant variant) {
        return ProductVariantManagementResponse.builder()
                .id(variant.getId())
                .originalPrice(variant.getOriginalPrice())
                .salePrice(variant.getSalePrice())
                .name(variant.getName())
                .nameColor(variant.getNameColor())
                .colorCode(variant.getColorCode())
                .saleStartAt(variant.getSaleStartAt())
                .saleEndAt(variant.getSaleEndAt())
                .stockQuantity(variant.getStockQuantity())
                .deleted(variant.isDeleted())
                .deletedAt(variant.getDeletedAt())
                .build();
    }

    // Mapper detail của 1 sản phẩm
    public ProductDetailResponse toProductDetailResponse(
            Product product,
            List<String> productImageUrls,
            List<ProductVariantDetailResponse> variants,
            Double averageRating,
            long totalReviews,
            List<ProductGroupItemResponse> groupProducts,
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
                .groupProducts(groupProducts)
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
            boolean isOwner,
            List<String> imageUrls,
            String videoUrl) {
        return ProductReviewItemResponse.builder()
                .reviewId(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .customerId(review.getCustomer().getId())
                .customerName(customerName)
                .isOwner(isOwner)
                .imageKeys(isOwner ? review.getImageKeys() : null)
                .videoKey(isOwner ? review.getVideoKey() : null)
                .imageUrls(imageUrls)
                .videoUrl(videoUrl)
                .build();
    }

    public ProductGroupItemResponse toProductGroupItemResponse(Product product, String imageUrl) {
        return ProductGroupItemResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .imageUrl(imageUrl)
                .build();
    }

    public ProductGroupResponse toProductGroupResponse(ProductGroup productGroup) {
        if (productGroup == null) {
            return null;
        }

        List<UUID> productIds = productGroup.getProducts() != null
                ? productGroup.getProducts().stream()
                        .filter(p -> !p.isDeleted())
                        .map(Product::getId)
                        .toList()
                : List.of();

        return ProductGroupResponse.builder()
                .id(productGroup.getId())
                .groupName(productGroup.getGroupName())
                .description(productGroup.getDescription())
                .deleted(productGroup.isDeleted())
                .deletedAt(productGroup.getDeletedAt())
                .updatedAt(productGroup.getUpdatedAt())
                .productIds(productIds)
                .productCount(productIds.size())
                .build();
    }

    public ProductManagementSummaryResponse toProductManagementSummaryResponse(
            Product product,
            String representativeImageUrl,
            List<ProductCategorySummaryResponse> categories,
            int variantCount) {
        ProductGroup group = product.getProductGroup();

        double price = 0.0;
        int stock = 0;

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            ProductVariant representativeVariant = product.getVariants().stream()
                    .filter(v -> !v.isDeleted())
                    .min(java.util.Comparator.comparing(ProductVariant::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .orElse(null);

            if (representativeVariant != null) {
                price = representativeVariant.getSalePrice() != null ? representativeVariant.getSalePrice() : representativeVariant.getOriginalPrice();
            }

            stock = product.getVariants().stream()
                    .filter(v -> !v.isDeleted())
                    .mapToInt(ProductVariant::getStockQuantity)
                    .sum();
        }

        String status = "IN_STOCK";
        if (stock == 0) {
            status = "OUT_OF_STOCK";
        } else if (stock <= 10) {
            status = "LOW_STOCK";
        }

        return ProductManagementSummaryResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .representativeImageUrl(representativeImageUrl)
                .productGroupId(group != null ? group.getId() : null)
                .productGroupName(group != null ? group.getGroupName() : null)
                .categories(categories)
                .variantCount(variantCount)
                .price(price)
                .stock(stock)
                .status(status)
                .deleted(product.isDeleted())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .deletedAt(product.getDeletedAt())
                .build();
    }

    public ProductManagementDetailResponse toProductManagementDetailResponse(
            Product product,
            String representativeImageUrl,
            List<String> productImageUrls,
            ProductGroupResponse productGroup,
            List<ProductCategorySummaryResponse> categories,
            List<ProductVariantManagementResponse> variants) {
        return ProductManagementDetailResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .specifications(product.getSpecifications())
                .compatibility(product.getCompatibility())
                .boxContents(product.getBoxContents())
                .supportInfo(product.getSupportInfo())
                .representativeImageKey(product.getRepresentativeImageKey())
                .representativeImageUrl(representativeImageUrl)
                .imageKeys(product.getImageKeys())
                .productImageUrls(productImageUrls)
                .productGroup(productGroup)
                .categories(categories)
                .variants(variants)
                .deleted(product.isDeleted())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .deletedAt(product.getDeletedAt())
                .build();
    }
}

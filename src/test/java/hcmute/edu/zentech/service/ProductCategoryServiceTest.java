package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CategoryProductListQueryRequest;
import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.PagedResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.CategoryProductSortOption;
import hcmute.edu.zentech.model.ImageProduct;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Spy
    private ProductMapper productMapper;

    @InjectMocks
    private ProductCategoryService productCategoryService;

    @Test
    void getProductsByCategoryId_throwsWhenCategoryNotFound() {
        UUID categoryId = UUID.randomUUID();
        when(productCategoryRepository.findCategoryWithProductsById(categoryId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productCategoryService.getProductsByCategoryId(categoryId, new CategoryProductListQueryRequest())
        );
    }

    @Test
    void getProductsByCategoryId_appliesSearchAndPagination() {
        Product matchingProductOne = buildProduct(
                "Alpha One",
                Instant.parse("2026-01-02T00:00:00Z"),
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg", "https://cdn.example.com/3.jpg"),
                300.0,
                250.0,
                List.of(5, 4)
        );
        Product nonMatchingProduct = buildProduct(
                "Beta",
                Instant.parse("2026-01-03T00:00:00Z"),
                List.of("https://cdn.example.com/4.jpg"),
                280.0,
                null,
                List.of(3)
        );
        Product matchingProductTwo = buildProduct(
                "Alpha Two",
                Instant.parse("2026-01-04T00:00:00Z"),
                List.of("https://cdn.example.com/5.jpg"),
                350.0,
                null,
                List.of(4, 4)
        );

        UUID categoryId = UUID.randomUUID();
        when(productCategoryRepository.findCategoryWithProductsById(categoryId))
                .thenReturn(Optional.of(buildCategory(matchingProductOne, nonMatchingProduct, matchingProductTwo)));

        CategoryProductListQueryRequest request = new CategoryProductListQueryRequest();
        request.setSearch(" alpha ");
        request.setPage(0);
        request.setSize(1);
        request.setSort(CategoryProductSortOption.NEWEST);

        PagedResponse<CategoryProductListItemResponse> response =
                productCategoryService.getProductsByCategoryId(categoryId, request);

        assertEquals(2, response.getTotalItems());
        assertEquals(2, response.getTotalPages());
        assertTrue(response.isHasNext());
        assertEquals(1, response.getItems().size());
        assertEquals("Alpha Two", response.getItems().get(0).getProductName());
        assertIterableEquals(
                List.of("https://cdn.example.com/5.jpg"),
                response.getItems().get(0).getImageUrls()
        );
    }

    @Test
    void getProductsByCategoryId_sortsByPriceAndFiltersByMinRating() {
        Product highPrice = buildProduct(
                "High Price",
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of("https://cdn.example.com/a.jpg"),
                500.0,
                450.0,
                List.of(5, 5)
        );
        Product lowPrice = buildProduct(
                "Low Price",
                Instant.parse("2026-01-02T00:00:00Z"),
                List.of("https://cdn.example.com/b.jpg", "https://cdn.example.com/c.jpg"),
                300.0,
                null,
                List.of(4, 4)
        );
        Product noReview = buildProduct(
                "No Review",
                Instant.parse("2026-01-03T00:00:00Z"),
                List.of("https://cdn.example.com/d.jpg"),
                100.0,
                90.0,
                List.of()
        );

        UUID categoryId = UUID.randomUUID();
        when(productCategoryRepository.findCategoryWithProductsById(categoryId))
                .thenReturn(Optional.of(buildCategory(highPrice, lowPrice, noReview)));

        CategoryProductListQueryRequest request = new CategoryProductListQueryRequest();
        request.setMinRating(4);
        request.setSort(CategoryProductSortOption.PRICE_ASC);

        PagedResponse<CategoryProductListItemResponse> response =
                productCategoryService.getProductsByCategoryId(categoryId, request);

        assertEquals(2, response.getItems().size());
        assertEquals("Low Price", response.getItems().get(0).getProductName());
        assertEquals(300.0, response.getItems().get(0).getOriginalPrice());
        assertEquals(null, response.getItems().get(0).getSalePrice());
        assertEquals(4.0, response.getItems().get(0).getAverageRating());
        assertIterableEquals(
                List.of("https://cdn.example.com/b.jpg", "https://cdn.example.com/c.jpg"),
                response.getItems().get(0).getImageUrls()
        );
        assertEquals("High Price", response.getItems().get(1).getProductName());
    }

    private ProductCategory buildCategory(Product... products) {
        ProductCategory category = new ProductCategory();
        category.setId(UUID.randomUUID());
        category.setCategoryName("Category");
        category.setProductList(new LinkedHashSet<>(List.of(products)));
        return category;
    }

    private Product buildProduct(
            String productName,
            Instant createdAt,
            List<String> imageUrls,
            Double originalPrice,
            Double salePrice,
            List<Integer> ratings
    ) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setProductName(productName);
        product.setCreatedAt(createdAt);
        product.setImageList(buildImages(product, imageUrls));
        product.setVariants(buildVariants(product, originalPrice, salePrice));
        product.setReviewList(buildReviews(product, ratings));
        product.setCategories(new LinkedHashSet<>());
        return product;
    }

    private Set<ImageProduct> buildImages(Product product, List<String> imageUrls) {
        Set<ImageProduct> images = new LinkedHashSet<>();
        for (String imageUrl : imageUrls) {
            ImageProduct imageProduct = new ImageProduct();
            imageProduct.setId(UUID.randomUUID());
            imageProduct.setImageUrl(imageUrl);
            imageProduct.setProduct(product);
            images.add(imageProduct);
        }
        return images;
    }

    private Set<ProductVariant> buildVariants(Product product, Double originalPrice, Double salePrice) {
        ProductVariant productVariant = new ProductVariant();
        productVariant.setId(UUID.randomUUID());
        productVariant.setOriginalPrice(originalPrice);
        productVariant.setSalePrice(salePrice);
        productVariant.setProduct(product);
        return new LinkedHashSet<>(List.of(productVariant));
    }

    private Set<ProductReview> buildReviews(Product product, List<Integer> ratings) {
        Set<ProductReview> reviews = new LinkedHashSet<>();
        for (Integer rating : ratings) {
            ProductReview review = new ProductReview();
            review.setId(UUID.randomUUID());
            review.setRating(rating);
            review.setProduct(product);
            reviews.add(review);
        }
        return reviews;
    }
}

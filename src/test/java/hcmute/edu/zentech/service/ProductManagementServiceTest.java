package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.MarkdownContentRequest;
import hcmute.edu.zentech.dto.request.ProductCreateRequest;
import hcmute.edu.zentech.dto.request.ProductUpdateRequest;
import hcmute.edu.zentech.dto.request.ProductVariantUpsertRequest;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductManagementServiceTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private ProductGroupRepository productGroupRepository;

    @Mock
    private R2StorageService r2StorageService;

    private ProductManagementService productManagementService;

    @BeforeEach
    void setUp() {
        productManagementService = new ProductManagementService(
                productRepository,
                categoryRepository,
                productGroupRepository,
                new ProductMapper(),
                r2StorageService
        );
    }

    @Test
    void createProductBuildsMarkdownFromStructuredSections() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = createCategory(categoryId, "Chargers");
        ProductCreateRequest request = new ProductCreateRequest();
        request.setProductName("Alpha65");
        request.setCategoryIds(List.of(categoryId));
        request.setImageKeys(List.of(" image-1.webp ", "image-1.webp", "image-2.webp"));
        request.setDescription(markdownContent());
        ProductVariantUpsertRequest variant = new ProductVariantUpsertRequest();
        variant.setOriginalPrice(1200000D);
        variant.setStockQuantity(5);
        request.setVariants(List.of(variant));

        when(categoryRepository.findAllById(List.of(categoryId))).thenReturn(List.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productManagementService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product product = productCaptor.getValue();
        assertThat(product.getDescription()).isEqualTo("""
                ## Dimensions
                Intro text
                - **Height:** 10 cm
                - Cable included
                """.trim());
        assertThat(product.getImageKeys()).containsExactly("image-1.webp", "image-2.webp");
        assertThat(product.getVariants()).hasSize(1);
        assertThat(product.getCategories()).containsExactly(category);
    }

    @Test
    void updateProductUpsertsVariantsAndSoftDeletesOmittedOnes() {
        UUID productId = UUID.randomUUID();
        UUID keptVariantId = UUID.randomUUID();
        UUID removedVariantId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setProductName("Old name");

        ProductVariant keptVariant = ProductVariant.builder()
                .id(keptVariantId)
                .product(product)
                .originalPrice(100D)
                .stockQuantity(1)
                .build();
        ProductVariant removedVariant = ProductVariant.builder()
                .id(removedVariantId)
                .product(product)
                .originalPrice(200D)
                .stockQuantity(2)
                .build();
        product.setVariants(new java.util.HashSet<>(Set.of(keptVariant, removedVariant)));

        ProductVariantUpsertRequest keptRequest = new ProductVariantUpsertRequest();
        keptRequest.setId(keptVariantId);
        keptRequest.setOriginalPrice(150D);
        keptRequest.setStockQuantity(3);

        ProductVariantUpsertRequest newRequest = new ProductVariantUpsertRequest();
        newRequest.setOriginalPrice(300D);
        newRequest.setStockQuantity(4);

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setVariants(List.of(keptRequest, newRequest));

        when(productRepository.findManagementDetailById(productId)).thenReturn(Optional.of(product));

        productManagementService.updateProduct(productId, request);

        assertThat(keptVariant.getOriginalPrice()).isEqualTo(150D);
        assertThat(keptVariant.getStockQuantity()).isEqualTo(3);
        assertThat(removedVariant.isDeleted()).isTrue();
        assertThat(removedVariant.getDeletedAt()).isNotNull();
        assertThat(product.getVariants())
                .filteredOn(variant -> variant.getId() == null)
                .singleElement()
                .satisfies(variant -> {
                    assertThat(variant.getOriginalPrice()).isEqualTo(300D);
                    assertThat(variant.getStockQuantity()).isEqualTo(4);
                    assertThat(variant.getProduct()).isSameAs(product);
                });
    }

    @Test
    void deleteProductSoftDeletesProductAndActiveVariants() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        ProductVariant variant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .product(product)
                .originalPrice(100D)
                .stockQuantity(1)
                .build();
        product.setVariants(new java.util.HashSet<>(Set.of(variant)));

        when(productRepository.findManagementDetailById(productId)).thenReturn(Optional.of(product));

        productManagementService.deleteProduct(productId);

        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getDeletedAt()).isNotNull();
        assertThat(variant.isDeleted()).isTrue();
        assertThat(variant.getDeletedAt()).isNotNull();
    }

    private MarkdownContentRequest markdownContent() {
        MarkdownContentRequest request = new MarkdownContentRequest();
        MarkdownContentRequest.MarkdownSectionRequest section = new MarkdownContentRequest.MarkdownSectionRequest();
        section.setHeading("Dimensions");
        section.setParagraphs(List.of("Intro text"));

        MarkdownContentRequest.MarkdownBulletRequest labeledBullet = new MarkdownContentRequest.MarkdownBulletRequest();
        labeledBullet.setLabel("Height");
        labeledBullet.setValue("10 cm");

        MarkdownContentRequest.MarkdownBulletRequest plainBullet = new MarkdownContentRequest.MarkdownBulletRequest();
        plainBullet.setValue("Cable included");

        section.setBullets(List.of(labeledBullet, plainBullet));
        request.setSections(List.of(section));
        return request;
    }

    private ProductCategory createCategory(UUID categoryId, String categoryName) {
        ProductCategory category = new ProductCategory();
        category.setId(categoryId);
        category.setCategoryName(categoryName);
        category.setShortName(categoryName);
        category.setPriority(1);
        return category;
    }
}

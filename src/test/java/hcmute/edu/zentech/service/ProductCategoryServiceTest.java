package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private R2StorageService r2StorageService;

    private ProductCategoryService productCategoryService;

    @BeforeEach
    void setUp() {
        productCategoryService = new ProductCategoryService(
                productCategoryRepository,
                new ProductMapper(),
                r2StorageService
        );
    }

    @Test
    void getAllCategoriesReturnsRootsAndChildrenSortedByPriority() {
        ProductCategory keyboards = createCategory("Keyboards", null, 1, null);
        ProductCategory mice = createCategory("Mercury Gaming Mouse", "Mice", 2, null);
        ProductCategory speakers = createCategory("Bluetooth Speaker", "Speakers", 3, null);
        ProductCategory earbuds = createCategory("Earbuds", "Earbuds", 4, null);
        ProductCategory chargers = createCategory("Chargers", "Chargers", 5, null);
        ProductCategory accessories = createCategory("Accessories", "Accessories", 6, null);
        ProductCategory mechanical = createCategory("Mechanical Keyboards for Gaming", "Mechanical Keyboard", 2, keyboards);
        ProductCategory hallEffect = createCategory("Hall Effect Keyboard", "HE Keyboard", 1, keyboards);

        when(productCategoryRepository.findAllWithParent()).thenReturn(List.of(
                chargers,
                mechanical,
                accessories,
                keyboards,
                speakers,
                hallEffect,
                earbuds,
                mice
        ));

        List<ProductCategorySummaryResponse> response = productCategoryService.getAllCategories();

        assertThat(response)
                .extracting(ProductCategorySummaryResponse::getCategoryName)
                .containsExactly(
                        "Keyboards",
                        "Mercury Gaming Mouse",
                        "Bluetooth Speaker",
                        "Earbuds",
                        "Chargers",
                        "Accessories"
                );
        assertThat(response.getFirst().getChildren())
                .extracting(ProductCategorySummaryResponse::getShortName)
                .containsExactly("HE Keyboard", "Mechanical Keyboard");
    }

    @Test
    void getAllCategoriesUsesNameAndIdFallbackWhenPriorityIsMissingOrDuplicated() {
        ProductCategory beta = createCategory("Beta", "Beta", 1, null);
        ProductCategory alpha = createCategory("Alpha", "Alpha", 1, null);
        ProductCategory missingPriority = createCategory("Missing", "Missing", null, null);

        when(productCategoryRepository.findAllWithParent()).thenReturn(List.of(missingPriority, beta, alpha));

        List<ProductCategorySummaryResponse> response = productCategoryService.getAllCategories();

        assertThat(response)
                .extracting(ProductCategorySummaryResponse::getShortName)
                .containsExactly("Alpha", "Beta", "Missing");
    }

    @Test
    void applyDefaultPrioritiesBackfillsExistingCategories() {
        ProductCategory keyboards = createCategory("Keyboards", null, 999, null);
        ProductCategory chargers = createCategory("Chargers", "Chargers", 999, null);
        ProductCategory hallEffect = createCategory("Hall Effect Keyboard", "HE Keyboard", 999, keyboards);

        when(productCategoryRepository.findAllWithParent()).thenReturn(List.of(chargers, hallEffect, keyboards));

        productCategoryService.applyDefaultPriorities();

        assertThat(keyboards.getPriority()).isEqualTo(1);
        assertThat(hallEffect.getPriority()).isEqualTo(1);
        assertThat(chargers.getPriority()).isEqualTo(5);
        verify(productCategoryRepository).saveAll(List.of(chargers, hallEffect, keyboards));
    }

    private ProductCategory createCategory(String categoryName, String shortName, Integer priority, ProductCategory parent) {
        ProductCategory category = new ProductCategory();
        category.setId(UUID.randomUUID());
        category.setCategoryName(categoryName);
        category.setShortName(shortName);
        category.setPriority(priority);
        category.setParent(parent);
        return category;
    }
}

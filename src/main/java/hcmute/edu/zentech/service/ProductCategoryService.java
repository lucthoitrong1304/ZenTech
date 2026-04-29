package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CategoryProductListQueryRequest;
import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.PagedResponse;
import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.CategoryProductSortOption;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    private static final int DEFAULT_CATEGORY_PRIORITY = 999;

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductMapper productMapper;

    // Tiêm thêm R2StorageService để gen link ảnh
    private final R2StorageService r2StorageService;

    public ProductCategory addCategory(String categoryName, String shortName, UUID categoryParentId) {
        return addCategory(categoryName, shortName, categoryParentId, DEFAULT_CATEGORY_PRIORITY);
    }

    public ProductCategory addCategory(String categoryName, String shortName, UUID categoryParentId, Integer priority) {
        ProductCategory newCategory = new ProductCategory();
        newCategory.setCategoryName(categoryName);
        newCategory.setShortName(shortName);
        newCategory.setPriority(priority == null ? DEFAULT_CATEGORY_PRIORITY : priority);

        if (categoryParentId != null) {
            ProductCategory parent = productCategoryRepository.findById(categoryParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryParentId));
            newCategory.setParent(parent);
        } else {
            newCategory.setParent(null);
        }

        return productCategoryRepository.save(newCategory);
    }

    @Transactional
    public void applyDefaultPriorities() {
        Map<String, Integer> rootPriorities = Map.of(
                "Keyboards", 1,
                "Mice", 2,
                "Speakers", 3,
                "Earbuds", 4,
                "Chargers", 5,
                "Accessories", 6
        );
        Map<String, Integer> keyboardChildPriorities = Map.of(
                "HE Keyboard", 1,
                "Mechanical Keyboard", 2
        );

        List<ProductCategory> changedCategories = productCategoryRepository.findAllWithParent().stream()
                .filter(category -> applyDefaultPriority(category, rootPriorities, keyboardChildPriorities))
                .toList();

        if (!changedCategories.isEmpty()) {
            productCategoryRepository.saveAll(changedCategories);
        }
    }

    private boolean applyDefaultPriority(
            ProductCategory category,
            Map<String, Integer> rootPriorities,
            Map<String, Integer> keyboardChildPriorities) {
        Integer expectedPriority;
        if (category.getParent() == null) {
            expectedPriority = rootPriorities.get(resolveCategoryKey(category));
        } else if ("Keyboards".equals(resolveCategoryKey(category.getParent()))) {
            expectedPriority = keyboardChildPriorities.get(resolveCategoryKey(category));
        } else {
            expectedPriority = null;
        }

        if (expectedPriority == null || Objects.equals(category.getPriority(), expectedPriority)) {
            return false;
        }

        category.setPriority(expectedPriority);
        return true;
    }

    private String resolveCategoryKey(ProductCategory category) {
        if (category.getShortName() != null && !category.getShortName().isBlank()) {
            return category.getShortName();
        }

        return category.getCategoryName();
    }

    public ProductCategory findCategoryByShortName(String shortName) {
        ProductCategory productCategory = productCategoryRepository.findCategoryByShortName(shortName);
        if (productCategory == null) {
            throw new ResourceNotFoundException("Product Category", "shortName", shortName);
        }
        return productCategory;
    }

    @Transactional(readOnly = true)
    public List<ProductCategorySummaryResponse> getAllCategories() {
        List<ProductCategory> categories = productCategoryRepository.findAllWithParent();
        Map<UUID, List<ProductCategory>> categoriesByParentId = categories.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return categories.stream()
                .filter(category -> category.getParent() == null)
                .sorted(buildCategoryComparator())
                .map(category -> buildCategoryTree(category, categoriesByParentId))
                .toList();
    }

    private ProductCategorySummaryResponse buildCategoryTree(
            ProductCategory category,
            Map<UUID, List<ProductCategory>> categoriesByParentId) {
        List<ProductCategorySummaryResponse> children = categoriesByParentId
                .getOrDefault(category.getId(), Collections.emptyList())
                .stream()
                .sorted(buildCategoryComparator())
                .map(child -> buildCategoryTree(child, categoriesByParentId))
                .toList();

        return productMapper.toProductCategorySummaryResponse(category, !children.isEmpty(), children);
    }

    private Comparator<ProductCategory> buildCategoryComparator() {
        return Comparator
                .comparing(ProductCategory::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProductCategory::getCategoryName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(ProductCategory::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CategoryProductListItemResponse> getProductsByCategoryId(
            UUID categoryId,
            CategoryProductListQueryRequest request) {
        // Tìm Category
        ProductCategory productCategory = productCategoryRepository.findCategoryWithProductsById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryId));

        List<ProductListingView> listingViews = productCategory.getProductList().stream()
                .filter(Objects::nonNull)
                .map(this::buildListingView)
                .filter(view -> matchesSearch(view, request.getSearch())) // Lọc theo keyword
                .filter(view -> matchesMinRating(view, request.getMinRating())) // Lọc theo rating
                .sorted(buildComparator(resolveSortOption(request.getSort()))) // Sort
                .toList();

        return buildPagedResponse(listingViews, request.getPage(), request.getSize());
    }

    // Sort
    private CategoryProductSortOption resolveSortOption(CategoryProductSortOption sortOption) {
        return sortOption == null ? CategoryProductSortOption.NEWEST : sortOption;
    }

    // Build 1 product render cho người dùng
    private ProductListingView buildListingView(Product product) {
        Optional<ProductVariant> representativeVariant = getRepresentativeVariant(product);
        Double originalPrice = representativeVariant.map(ProductVariant::getOriginalPrice).orElse(null);
        Double salePrice = representativeVariant.map(ProductVariant::getSalePrice).orElse(null);
        Double effectivePrice = salePrice != null ? salePrice : originalPrice;

        return new ProductListingView(
                product,
                getRepresentativeImageUrl(product),
                originalPrice,
                salePrice,
                effectivePrice,
                getAverageRating(product)
        );
    }

    // Lấy biến thể sản phẩm đầu tiên
    private Optional<ProductVariant> getRepresentativeVariant(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return Optional.empty();
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    // Ưu tiên ảnh đại diện đã chọn, fallback ảnh đầu tiên trong gallery.
    private String getRepresentativeImageUrl(Product product) {
        String representativeImageKey = getRepresentativeImageKey(product);
        if (representativeImageKey == null) {
            return null;
        }

        return r2StorageService.getPresignedGetUrl(representativeImageKey);
    }

    private String getRepresentativeImageKey(Product product) {
        if (product.getRepresentativeImageKey() != null && !product.getRepresentativeImageKey().isBlank()) {
            return product.getRepresentativeImageKey();
        }

        if (product.getImageKeys() == null || product.getImageKeys().isEmpty()) {
            return null;
        }

        return product.getImageKeys().getFirst();
    }

    // Tính trung bình điểm đánh giá của 1 sản phẩm.
    private Double getAverageRating(Product product) {
        if (product.getReviewList() == null || product.getReviewList().isEmpty()) {
            return null;
        }

        return product.getReviewList().stream()
                .filter(Objects::nonNull)
                .map(ProductReview::getRating)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    // Filter Search
    private boolean matchesSearch(ProductListingView view, String search) {
        if (search == null || search.trim().isEmpty()) {
            return true;
        }

        String productName = view.product().getProductName();
        if (productName == null) {
            return false;
        }

        return productName.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }

    // Filter Rating
    private boolean matchesMinRating(ProductListingView view, Integer minRating) {
        if (minRating == null) {
            return true;
        }

        return view.averageRating() != null && view.averageRating() >= minRating;
    }

    // Logic sort:
    // Nếu 2 sản phẩm bằng nhau => thenComparing được chạy => Id nào nhỏ hơn nằm trước
    // Các đối tượng nào null => nằm cuối ds.
    private Comparator<ProductListingView> buildComparator(CategoryProductSortOption sortOption) {
        Comparator<ProductListingView> productIdComparator =
                Comparator.comparing(ProductListingView::productId, Comparator.nullsLast(Comparator.naturalOrder()));

        return switch (sortOption) {
            case PRICE_ASC -> Comparator
                    .comparing(ProductListingView::effectivePrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(productIdComparator);
            case PRICE_DESC -> Comparator
                    .comparing(ProductListingView::effectivePrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(productIdComparator);
            case RATING_ASC -> Comparator
                    .comparing(ProductListingView::averageRating, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(productIdComparator);
            case RATING_DESC -> Comparator
                    .comparing(ProductListingView::averageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(productIdComparator);
            case OLDEST -> Comparator
                    .comparing(ProductListingView::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(productIdComparator);
            case NEWEST -> Comparator
                    .comparing(ProductListingView::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(productIdComparator);
        };
    }

    private PagedResponse<CategoryProductListItemResponse> buildPagedResponse(
            List<ProductListingView> listingViews,
            int page,
            int size) {
        int totalItems = listingViews.size();
        // Math.ceil => Làm tròn lên
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

        // Logic tính toán phạm vi lấy từ đâu đến đâu
        long offset = (long) page * size; // Dùng để bỏ qua bao nhiu phần tử
        int fromIndex = offset >= totalItems ? totalItems : (int) offset; // Vị trí bắt đầu
        int toIndex = Math.min(fromIndex + size, totalItems); // Vị trí kết thúc

        // Sublist: Cắt một đoạn từ danh sách.
        List<CategoryProductListItemResponse> items = listingViews.subList(fromIndex, toIndex).stream()
                .map(view -> productMapper.toCategoryProductListItemResponse(
                        view.product(),
                        view.imageUrl(),
                        view.originalPrice(),
                        view.salePrice(),
                        view.averageRating()
                ))
                .toList();

        return PagedResponse.<CategoryProductListItemResponse>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .hasNext(toIndex < totalItems)
                .hasPrevious(page > 0)
                .build();
    }

    // View Object
    private record ProductListingView(
            Product product,
            String imageUrl,
            Double originalPrice,
            Double salePrice,
            Double effectivePrice,
            Double averageRating) { // Điểm đánh giá trung bình
        private UUID productId() {
            return product.getId();
        }

        private Instant createdAt() {
            return product.getCreatedAt();
        }
    }

    @Transactional(readOnly = true)
    public long count() {
        return productCategoryRepository.count();
    }
}

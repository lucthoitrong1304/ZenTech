package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ProductSearchQueryRequest;
import hcmute.edu.zentech.dto.request.VariantRequestDTO;
import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.ProductDetailResponse;
import hcmute.edu.zentech.dto.response.ProductGroupItemResponse;
import hcmute.edu.zentech.dto.response.ProductVariantDetailResponse;
import hcmute.edu.zentech.dto.response.PagedResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.CategoryProductSortOption;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductReview;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final int MAX_SIMILAR_PRODUCTS = 4;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductReviewRepository productReviewRepository;
    private final ProductVariantService productVariantService;
    private final ProductMapper productMapper;
    private final R2StorageService r2StorageService;

    @Transactional
    public Product addProduct(
            String productName,
            String specifications,
            String compatibility,
            String boxContents,
            String supportInfo,
            UUID categoryId,
            List<VariantRequestDTO> variantDataList) {

        // 1. Kiểm tra và lấy danh mục
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryId));

        // 2. Khởi tạo đối tượng Product
        Product product = new Product();
        product.setProductName(productName);
        product.setSpecifications(specifications);
        product.setCompatibility(compatibility);
        product.setBoxContents(boxContents);
        product.setSupportInfo(supportInfo);
        product.setCategories(new HashSet<>(Set.of(category)));

        // 3. Xử lý danh sách biến thể
        if (variantDataList != null && !variantDataList.isEmpty()) {
            Set<ProductVariant> managedVariants = new HashSet<>();
            for (VariantRequestDTO dto : variantDataList) {
                ProductVariant newVariant = productVariantService.buildProductVariant(product, dto);
                managedVariants.add(newVariant);
            }
            product.setVariants(managedVariants);
        }

        // 4. Lưu vào Database (Nhờ CascadeType.ALL, các Variants cũng sẽ được tự động lưu)
        return productRepository.save(product);
    }

    @Transactional
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void setSeedDescriptionIfMissing(String productName, String description) {
        productRepository.findFirstByProductNameIgnoreCaseAndDeletedFalse(productName)
                .filter(product -> product.getDescription() == null || product.getDescription().isBlank())
                .ifPresent(product -> {
                    product.setDescription(description);
                    productRepository.save(product);
                });
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(UUID productId) {
        // Lấy chi tiết 1 sản phẩm
        Product product = productRepository.findProductDetailById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "ID", productId));

        // Tính điểm trung bình đánh giá sản phẩm
        Double averageRating = productReviewRepository.findAverageRatingByProductId(productId);
        // Tổng số lượt review
        long totalReviews = productReviewRepository.countByProduct_Id(productId);

        List<String> productImageUrls = getProductImageUrls(product);
        List<ProductVariantDetailResponse> variants = getSortedVariants(product);
        List<ProductGroupItemResponse> groupProducts = getGroupProducts(product);
        List<CategoryProductListItemResponse> similarProducts = getSimilarProducts(product);

        return productMapper.toProductDetailResponse(
                product,
                productImageUrls,
                variants,
                averageRating,
                totalReviews,
                groupProducts,
                similarProducts
        );
    }

    private List<CategoryProductListItemResponse> getSimilarProducts(Product product) {
        // Lấy danh sách category của product
        Set<UUID> currentCategoryIds = getCategoryIds(product);
        if (currentCategoryIds.isEmpty()) {
            return List.of();
        }

        // Lấy giá trị thực sự của sản phẩm biến thể đầu tiên của 1 sản phẩm
        Double currentEffectivePrice = getRepresentativeVariant(product)
                .map(this::getEffectivePrice)
                .orElse(null);

        // 1. Tìm toàn bộ sản phẩm trong cùng 1 category
        // 2. Lọc các ứng viên mà id không phải là sản phẩm hiện tại.
        // 3. Giải thích toMap:
            // 3.1 Tham số thứ 1: KeyMapper
            // 3.2 Tham số thứ 2: Giá trị Mapper
            // 3.3 Tham số thứ 3: Merge Function
            // 3.4 Tham số thứ 4: Map Supplier
        // Dùng LinkedHashMap để đảm bảo thứ tự.
        // => Mục đích của hàm là tìm ra các ứng viên duy nhất không bị trùng lặp
        List<Product> uniqueCandidates = productRepository.findProductsForSimilarityByCategoryIds(currentCategoryIds).stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !Objects.equals(candidate.getId(), product.getId()))
                .collect(Collectors.toMap(
                        Product::getId,
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        return uniqueCandidates.stream()
                .map(candidate -> buildSimilarProductView(candidate, currentCategoryIds, currentEffectivePrice))
                .sorted(buildSimilarProductComparator())
                .limit(MAX_SIMILAR_PRODUCTS)
                .map(view -> productMapper.toCategoryProductListItemResponse(
                        view.product(),
                        view.imageUrl(),
                        view.originalPrice(),
                        view.salePrice(),
                        view.averageRating(),
                        view.stockQuantity()
                ))
                .toList();
    }

    private List<ProductVariantDetailResponse> getSortedVariants(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return List.of();
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(productMapper::toProductVariantDetailResponse)
                .toList();
    }

    private List<ProductGroupItemResponse> getGroupProducts(Product product) {
        if (product.getProductGroup() == null || product.getProductGroup().getId() == null) {
            return List.of();
        }

        return productRepository.findGroupProducts(product.getProductGroup().getId(), product.getId()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Product::getProductName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Product::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(groupProduct -> productMapper.toProductGroupItemResponse(
                        groupProduct,
                        getRepresentativeImageUrl(groupProduct)
                ))
                .toList();
    }

    private SimilarProductView buildSimilarProductView(
            Product product,
            Set<UUID> currentCategoryIds,
            Double currentEffectivePrice) {

        Optional<ProductVariant> representativeVariant = getRepresentativeVariant(product);
        Double originalPrice = representativeVariant.map(ProductVariant::getOriginalPrice).orElse(null);
        Double salePrice = representativeVariant.map(ProductVariant::getSalePrice).orElse(null);
        Double effectivePrice = salePrice != null ? salePrice : originalPrice;
        boolean hasComparablePrice = currentEffectivePrice != null && effectivePrice != null;
        Integer stockQuantity = product.getVariants() != null ? product.getVariants().stream()
                .filter(Objects::nonNull)
                .filter(variant -> !variant.isDeleted())
                .mapToInt(ProductVariant::getStockQuantity)
                .sum() : 0;

        return new SimilarProductView(
                product,
                getRepresentativeImageUrl(product),
                originalPrice,
                salePrice,
                getAverageRating(product),
                countSharedCategoryIds(product, currentCategoryIds),
                hasComparablePrice,
                hasComparablePrice ? Math.abs(effectivePrice - currentEffectivePrice) : null,
                stockQuantity
        );
    }

    private Comparator<SimilarProductView> buildSimilarProductComparator() {
        return Comparator
                .comparingInt(SimilarProductView::sharedCategoryCount)
                .reversed()
                .thenComparing(SimilarProductView::hasComparablePrice, Comparator.reverseOrder())
                .thenComparing(SimilarProductView::priceDifference, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SimilarProductView::averageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SimilarProductView::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SimilarProductView::productId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    // Lấy danh sách id category của 1 sản phẩm
    private Set<UUID> getCategoryIds(Product product) {
        if (product.getCategories() == null || product.getCategories().isEmpty()) {
            return Set.of();
        }
        return product.getCategories().stream()
                .filter(Objects::nonNull)
                .map(ProductCategory::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // Đếm xem sản phẩm đó nằm ở bao nhiu category
    private int countSharedCategoryIds(Product product, Set<UUID> currentCategoryIds) {
        if (product.getCategories() == null || product.getCategories().isEmpty()) {
            return 0;
        }
        return (int) product.getCategories().stream()
                .filter(Objects::nonNull)
                .map(ProductCategory::getId)
                .filter(Objects::nonNull)
                .filter(currentCategoryIds::contains)
                .count();
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

    // Lấy giá tiền của sản phẩm biến thể.
    private Double getEffectivePrice(ProductVariant variant) {
        return variant.getSalePrice() != null ? variant.getSalePrice() : variant.getOriginalPrice();
    }

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

    private List<String> getProductImageUrls(Product product) {
        if (product.getImageKeys() == null || product.getImageKeys().isEmpty()) {
            return List.of();
        }

        return product.getImageKeys().stream()
                .map(r2StorageService::getPresignedGetUrl)
                .filter(Objects::nonNull)
                .toList();
    }

    // Tính điểm đánh giá trung bình
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

    // Object build sản phẩm tương tự
    private record SimilarProductView(
            Product product,
            String imageUrl,
            Double originalPrice,
            Double salePrice,
            Double averageRating,
            int sharedCategoryCount,
            boolean hasComparablePrice,
            Double priceDifference,
            Integer stockQuantity) {
        private UUID productId() {
            return product.getId();
        }
        private Instant createdAt() {
            return product.getCreatedAt();
        }
    }

    @Transactional(readOnly = true)
    public boolean existsByProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            return false;
        }
        return productRepository.existsByProductName(productName);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CategoryProductListItemResponse> getProducts(ProductSearchQueryRequest request) {
        String keyword = request.getSearch() != null ? request.getSearch().trim() : null;
        if (keyword != null && keyword.isEmpty()) {
            keyword = null;
        }

        List<Product> products = productRepository.searchActiveProductsWithVariantsAndReviews(keyword);

        List<ProductListingView> listingViews = products.stream()
                .filter(Objects::nonNull)
                .map(this::buildListingView)
                .filter(view -> matchesMinRating(view, request.getMinRating()))
                .sorted(buildComparator(resolveSortOption(request.getSort())))
                .toList();

        return buildPagedResponse(listingViews, request.getPage(), request.getSize());
    }

    private ProductListingView buildListingView(Product product) {
        Optional<ProductVariant> representativeVariant = getRepresentativeVariant(product);
        Double originalPrice = representativeVariant.map(ProductVariant::getOriginalPrice).orElse(null);
        Double salePrice = representativeVariant.map(ProductVariant::getSalePrice).orElse(null);
        Double effectivePrice = salePrice != null ? salePrice : originalPrice;
        Integer stockQuantity = product.getVariants() != null ? product.getVariants().stream()
                .filter(Objects::nonNull)
                .filter(variant -> !variant.isDeleted())
                .mapToInt(ProductVariant::getStockQuantity)
                .sum() : 0;

        return new ProductListingView(
                product,
                getRepresentativeImageUrl(product),
                originalPrice,
                salePrice,
                effectivePrice,
                getAverageRating(product),
                stockQuantity
        );
    }

    private boolean matchesMinRating(ProductListingView view, Integer minRating) {
        if (minRating == null) {
            return true;
        }
        return view.averageRating() != null && view.averageRating() >= minRating;
    }

    private CategoryProductSortOption resolveSortOption(CategoryProductSortOption sortOption) {
        return sortOption == null ? CategoryProductSortOption.NEWEST : sortOption;
    }

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
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

        long offset = (long) page * size;
        int fromIndex = offset >= totalItems ? totalItems : (int) offset;
        int toIndex = Math.min(fromIndex + size, totalItems);

        List<CategoryProductListItemResponse> items = listingViews.subList(fromIndex, toIndex).stream()
                .map(view -> productMapper.toCategoryProductListItemResponse(
                        view.product(),
                        view.imageUrl(),
                        view.originalPrice(),
                        view.salePrice(),
                        view.averageRating(),
                        view.stockQuantity()
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

    private record ProductListingView(
            Product product,
            String imageUrl,
            Double originalPrice,
            Double salePrice,
            Double effectivePrice,
            Double averageRating,
            Integer stockQuantity) {
        private UUID productId() {
            return product.getId();
        }

        private Instant createdAt() {
            return product.getCreatedAt();
        }
    }
}

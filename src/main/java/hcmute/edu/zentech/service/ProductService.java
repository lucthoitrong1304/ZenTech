package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.VariantRequestDTO;
import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.dto.response.ProductDetailResponse;
import hcmute.edu.zentech.dto.response.ProductVariantDetailResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.ImageProduct;
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
    private static final int MAX_SIMILAR_PRODUCTS = 4; // Số lượng lấy tối đa - sản phẩm tương tự

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductReviewRepository productReviewRepository;
    private final ProductVariantService productVariantService;
    private final ProductMapper productMapper;

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

            // Gắn danh sách Con vào Cha
            product.setVariants(managedVariants);
        }

        // 4. Lưu vào Database (Nhờ CascadeType.ALL, các Variants cũng sẽ được tự động lưu)
        return productRepository.save(product);
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

        // Lấy danh sách ảnh - danh sách biến thể - danh sách sản phẩm tương tự
        List<String> productImageUrls = getSortedProductImageUrls(product);
        List<ProductVariantDetailResponse> variants = getSortedVariants(product);
        List<CategoryProductListItemResponse> similarProducts = getSimilarProducts(product);

        return productMapper.toProductDetailResponse(
                product,
                productImageUrls,
                variants,
                averageRating,
                totalReviews,
                similarProducts
        );
    }

    // Danh sách ảnh sort tăng dần theo id ảnh
    private List<String> getSortedProductImageUrls(Product product) {
        if (product.getImageList() == null || product.getImageList().isEmpty()) {
            return List.of();
        }

        return product.getImageList().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ImageProduct::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ImageProduct::getImageUrl)
                .filter(Objects::nonNull)
                .toList();
    }

    // Lấy danh sách sản phẩm tương tự
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
                        (left, right) -> left, // Giứ cái đầu tiên bỏ cái sau nếu 2 phần tử trùng nhau về id
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
                        view.imageUrls(),
                        view.originalPrice(),
                        view.salePrice(),
                        view.averageRating()
                ))
                .toList();
    }

    // Lấy danh sách biến thể của 1 sản phẩm
    private List<ProductVariantDetailResponse> getSortedVariants(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return List.of();
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(variant -> productMapper.toProductVariantDetailResponse(
                        variant,
                        variant.getImageUrls() == null ? List.of() : List.copyOf(variant.getImageUrls())
                ))
                .toList();
    }

    // Build 1 thông số của 1 sản phẩm tương tự
    private SimilarProductView buildSimilarProductView(
            Product product,
            Set<UUID> currentCategoryIds,
            Double currentEffectivePrice) {
        // 1. Lấy sản phẩm biến thể đại diện của sản phẩm
        // 2. Lấy giá gốc
        // 3. Lấy giá sale
        // 4. Lấy giá trị thực
        Optional<ProductVariant> representativeVariant = getRepresentativeVariant(product);
        Double originalPrice = representativeVariant.map(ProductVariant::getOriginalPrice).orElse(null);
        Double salePrice = representativeVariant.map(ProductVariant::getSalePrice).orElse(null);
        Double effectivePrice = salePrice != null ? salePrice : originalPrice;
        boolean hasComparablePrice = currentEffectivePrice != null && effectivePrice != null;

        return new SimilarProductView(
                product,
                getListingImageUrls(product),
                originalPrice,
                salePrice,
                getAverageRating(product),
                countSharedCategoryIds(product, currentCategoryIds),
                hasComparablePrice,
                hasComparablePrice ? Math.abs(effectivePrice - currentEffectivePrice) : null
        );
    }

    // Hàm so sánh để build sản phẩm tương tự
    // Độ tương đồng về danh mục sản phẩm - ưu tiên cao nhất
    // Có thể so sánh giá: Lấy true
    // Độ lệch giá: Lấy nhỏ hơn
    // Độ đánh giá trung bình: Lấy nhỏ hơn
    // Ngày tạo: Lấy gần nhất
    // id Tăng dần
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

    // Lấy 2 ảnh của 1 sản phẩm
    private List<String> getListingImageUrls(Product product) {
        return getSortedProductImageUrls(product).stream()
                .limit(2)
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
            List<String> imageUrls,
            Double originalPrice,
            Double salePrice,
            Double averageRating,
            int sharedCategoryCount,
            boolean hasComparablePrice,
            Double priceDifference) {
        private UUID productId() {
            return product.getId();
        }

        private Instant createdAt() {
            return product.getCreatedAt();
        }
    }
}

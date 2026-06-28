package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CategoryManagementRequest;
import hcmute.edu.zentech.dto.request.CategoryProductListQueryRequest;
import hcmute.edu.zentech.dto.request.CategoryReorderItemRequest;
import hcmute.edu.zentech.dto.request.CategoryReorderRequest;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    private static final int DEFAULT_CATEGORY_PRIORITY = 999;

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductMapper productMapper;

    // Dùng để tạo presigned URL cho ảnh sản phẩm lưu trên R2.
    private final R2StorageService r2StorageService;

    /**
     * Tạo danh mục sản phẩm mới.
     *
     * @param categoryName tên đầy đủ của danh mục
     * @param shortName tên rút gọn dùng để hiển thị
     * @param categoryParentId id danh mục cha, null nếu là danh mục gốc
     * @param priority thứ tự hiển thị, null sẽ dùng độ ưu tiên mặc định
     * @return danh mục vừa được lưu
     */
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

    /**
     * Áp dụng thứ tự mặc định cho các danh mục có sẵn.
     */
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

    /**
     * Cập nhật priority cho một danh mục nếu có cấu hình mặc định tương ứng.
     *
     * @param category danh mục cần kiểm tra priority
     * @param rootPriorities map priority cho danh mục gốc
     * @param keyboardChildPriorities map priority cho danh mục con của Keyboards
     * @return true nếu danh mục được thay đổi priority
     */
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

    /**
     * Lấy khóa định danh danh mục để so với cấu hình priority.
     *
     * @param category danh mục cần lấy tên so khớp
     * @return shortName nếu có, ngược lại là categoryName
     */
    private String resolveCategoryKey(ProductCategory category) {
        if (category.getShortName() != null && !category.getShortName().isBlank()) {
            return category.getShortName();
        }

        return category.getCategoryName();
    }

    /**
     * Tìm danh mục theo shortName.
     *
     * @param shortName tên rút gọn của danh mục
     * @return danh mục khớp shortName
     */
    public ProductCategory findCategoryByShortName(String shortName) {
        ProductCategory productCategory = productCategoryRepository.findCategoryByShortName(shortName);
        if (productCategory == null) {
            throw new ResourceNotFoundException("Product Category", "shortName", shortName);
        }
        return productCategory;
    }

    /**
     * Lấy cây danh mục công khai cho khách hàng.
     *
     * @return danh sách danh mục gốc kèm danh mục con đang hiển thị
     */
    @Transactional(readOnly = true)
    public List<ProductCategorySummaryResponse> getAllCategories() {
        return getCategoryTree(false);
    }

    /**
     * Lấy cây danh mục cho trang quản lý.
     *
     * @return danh sách danh mục gốc kèm danh mục con, bao gồm danh mục bị ẩn
     */
    @Transactional(readOnly = true)
    public List<ProductCategorySummaryResponse> getAllManagementCategories() {
        return getCategoryTree(true);
    }

    /**
     * Dựng cây danh mục cha-con.
     *
     * @param includeHidden true nếu cần lấy cả danh mục bị ẩn
     * @return cây danh mục đã sắp xếp theo priority và tên
     */
    private List<ProductCategorySummaryResponse> getCategoryTree(boolean includeHidden) {
        List<ProductCategory> categories = productCategoryRepository.findAllWithParent();
        List<ProductCategory> visibleCategories = includeHidden
                ? categories
                : categories.stream().filter(ProductCategory::isVisible).toList();
        Map<UUID, List<ProductCategory>> categoriesByParentId = visibleCategories.stream()
                .filter(category -> category.getParent() != null)
                .filter(category -> includeHidden || category.getParent().isVisible())
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        return visibleCategories.stream()
                .filter(category -> category.getParent() == null)
                .sorted(buildCategoryComparator())
                .map(category -> buildCategoryTree(category, categoriesByParentId))
                .toList();
    }

    /**
     * Dựng response cho một nhánh danh mục.
     *
     * @param category danh mục hiện tại
     * @param categoriesByParentId map danh mục con theo id danh mục cha
     * @return response danh mục hiện tại kèm các danh mục con
     */
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

    /**
     * Tạo comparator dùng để sắp xếp danh mục.
     *
     * @return comparator ưu tiên priority, sau đó tên và id
     */
    private Comparator<ProductCategory> buildCategoryComparator() {
        return Comparator
                .comparing(ProductCategory::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProductCategory::getCategoryName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(ProductCategory::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Tạo danh mục từ trang quản lý.
     *
     * @param request dữ liệu tên, shortName, trạng thái hiển thị và danh mục cha
     * @return thông tin danh mục vừa tạo
     */
    @Transactional
    public ProductCategorySummaryResponse createManagementCategory(CategoryManagementRequest request) {
        ProductCategory category = new ProductCategory();
        applyEditableFields(category, request);
        category.setPriority(resolveNextPriority(request.getParentId()));
        return buildSingleCategoryResponse(productCategoryRepository.save(category));
    }

    /**
     * Cập nhật danh mục từ trang quản lý.
     *
     * @param categoryId id danh mục cần cập nhật
     * @param request dữ liệu mới của danh mục
     * @return thông tin danh mục sau khi cập nhật
     */
    @Transactional
    public ProductCategorySummaryResponse updateManagementCategory(UUID categoryId, CategoryManagementRequest request) {
        ProductCategory category = findCategoryOrThrow(categoryId);
        UUID parentId = request.getParentId();
        if (parentId != null) {
            validateParent(categoryId, parentId);
        }
        applyEditableFields(category, request);
        return buildSingleCategoryResponse(productCategoryRepository.save(category));
    }

    /**
     * Xóa danh mục quản lý nếu danh mục không còn con và không gắn sản phẩm.
     *
     * @param categoryId id danh mục cần xóa
     * @return thông tin danh mục trước khi bị xóa
     */
    @Transactional
    public ProductCategorySummaryResponse deleteManagementCategory(UUID categoryId) {
        ProductCategory category = findCategoryOrThrow(categoryId);
        if (productCategoryRepository.existsByParent_Id(categoryId)) {
            throw new RuntimeException("Không thể xóa danh mục đang có danh mục con.");
        }
        if (productCategoryRepository.countActiveProductsByCategoryId(categoryId) > 0) {
            throw new RuntimeException("Không thể xóa danh mục đang được gắn với sản phẩm.");
        }

        ProductCategorySummaryResponse response = buildSingleCategoryResponse(category);
        productCategoryRepository.delete(category);
        return response;
    }

    /**
     * Cập nhật lại thứ tự và quan hệ cha-con của nhiều danh mục.
     *
     * @param request danh sách danh mục kèm parentId và priority mới
     * @return cây danh mục quản lý sau khi sắp xếp lại
     */
    @Transactional
    public List<ProductCategorySummaryResponse> reorderManagementCategories(CategoryReorderRequest request) {
        List<ProductCategory> categories = productCategoryRepository.findAllWithParent();
        Map<UUID, ProductCategory> categoryById = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, category -> category));
        Map<UUID, UUID> parentById = new HashMap<>();

        for (CategoryReorderItemRequest item : request.getItems()) {
            ProductCategory category = categoryById.get(item.getId());
            if (category == null) {
                throw new ResourceNotFoundException("Product Category", "ID", item.getId());
            }
            UUID parentId = item.getParentId();
            if (parentId != null && !categoryById.containsKey(parentId)) {
                throw new ResourceNotFoundException("Product Category", "ID", parentId);
            }
            if (Objects.equals(item.getId(), parentId)) {
                throw new RuntimeException("Danh mục không thể là cha của chính nó.");
            }
            parentById.put(item.getId(), parentId);
        }

        for (ProductCategory category : categories) {
            parentById.putIfAbsent(
                    category.getId(),
                    category.getParent() == null ? null : category.getParent().getId()
            );
        }

        for (UUID categoryId : parentById.keySet()) {
            validateNoCycle(categoryId, parentById);
        }

        for (CategoryReorderItemRequest item : request.getItems()) {
            ProductCategory category = categoryById.get(item.getId());
            category.setParent(item.getParentId() == null ? null : categoryById.get(item.getParentId()));
            category.setPriority(item.getPriority());
        }

        productCategoryRepository.saveAll(categories);
        return getAllManagementCategories();
    }

    /**
     * Tìm danh mục theo id hoặc báo lỗi nếu không tồn tại.
     *
     * @param categoryId id danh mục cần tìm
     * @return danh mục tìm được
     */
    private ProductCategory findCategoryOrThrow(UUID categoryId) {
        return productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryId));
    }

    /**
     * Gán các trường được phép chỉnh sửa từ request vào entity.
     *
     * @param category entity danh mục cần cập nhật
     * @param request dữ liệu nhập từ trang quản lý
     */
    private void applyEditableFields(ProductCategory category, CategoryManagementRequest request) {
        category.setCategoryName(normalizeRequiredCategoryName(request.getCategoryName()));
        category.setShortName(normalizeOptionalText(request.getShortName()));
        category.setVisible(request.getVisible() == null || request.getVisible());

        UUID parentId = request.getParentId();
        category.setParent(parentId == null ? null : findCategoryOrThrow(parentId));
    }

    /**
     * Kiểm tra parent mới hợp lệ và không tạo vòng lặp.
     *
     * @param categoryId id danh mục đang chỉnh sửa
     * @param parentId id danh mục cha mới
     */
    private void validateParent(UUID categoryId, UUID parentId) {
        if (Objects.equals(categoryId, parentId)) {
            throw new RuntimeException("Danh mục không thể là cha của chính nó.");
        }

        List<ProductCategory> categories = productCategoryRepository.findAllWithParent();
        Map<UUID, UUID> parentById = new HashMap<>();
        for (ProductCategory category : categories) {
            parentById.put(
                    category.getId(),
                    category.getParent() == null ? null : category.getParent().getId()
            );
        }
        parentById.put(categoryId, parentId);
        validateNoCycle(categoryId, parentById);
    }

    /**
     * Kiểm tra cây danh mục không có vòng lặp cha-con.
     *
     * @param categoryId id danh mục bắt đầu kiểm tra
     * @param parentById map id danh mục sang id cha
     */
    private void validateNoCycle(UUID categoryId, Map<UUID, UUID> parentById) {
        Set<UUID> visited = new HashSet<>();
        UUID currentId = categoryId;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new RuntimeException("Không thể tạo vòng lặp trong cây danh mục.");
            }
            currentId = parentById.get(currentId);
        }
    }

    /**
     * Tính priority kế tiếp trong cùng một nhóm cha.
     *
     * @param parentId id danh mục cha, null nếu thuộc cấp gốc
     * @return priority mới lớn hơn các danh mục cùng cấp hiện có
     */
    private int resolveNextPriority(UUID parentId) {
        return productCategoryRepository.findAllWithParent().stream()
                .filter(category -> Objects.equals(
                        category.getParent() == null ? null : category.getParent().getId(),
                        parentId
                ))
                .map(ProductCategory::getPriority)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(priority -> priority + 1)
                .orElse(1);
    }

    /**
     * Chuyển một entity danh mục thành response đơn lẻ.
     *
     * @param category danh mục cần chuyển đổi
     * @return response không kèm danh mục con
     */
    private ProductCategorySummaryResponse buildSingleCategoryResponse(ProductCategory category) {
        return productMapper.toProductCategorySummaryResponse(category, false, List.of());
    }

    /**
     * Chuẩn hóa tên danh mục bắt buộc.
     *
     * @param value tên danh mục người dùng nhập, có thể chứa khoảng trắng đầu/cuối
     * @return tên danh mục đã được trim
     */
    private String normalizeRequiredCategoryName(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new RuntimeException("categoryName is required");
        }
        return normalized;
    }

    /**
     * Chuẩn hóa chuỗi không bắt buộc.
     *
     * @param value chuỗi đầu vào có thể null hoặc rỗng
     * @return chuỗi đã trim, hoặc null nếu không có nội dung
     */
    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Lấy danh sách sản phẩm thuộc một danh mục và các danh mục con.
     *
     * @param categoryId id danh mục cần xem sản phẩm
     * @param request bộ lọc, phân trang và kiểu sắp xếp
     * @return danh sách sản phẩm đã lọc, sắp xếp và phân trang
     */
    @Transactional(readOnly = true)
    public PagedResponse<CategoryProductListItemResponse> getProductsByCategoryId(
            UUID categoryId,
            CategoryProductListQueryRequest request) {
        // Tìm toàn bộ danh mục để dựng cây quan hệ cha-con
        List<ProductCategory> allCategories = productCategoryRepository.findAllWithParent();

        // Kiểm tra xem danh mục mục tiêu có tồn tại hay không
        ProductCategory targetCategory = allCategories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryId));
        if (!targetCategory.isVisible()) {
            throw new ResourceNotFoundException("Product Category", "ID", categoryId);
        }

        // Nhóm các danh mục con theo parent_id
        Map<UUID, List<ProductCategory>> childrenMap = allCategories.stream()
                .filter(ProductCategory::isVisible)
                .filter(c -> c.getParent() != null)
                .filter(c -> c.getParent().isVisible())
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        // Thu thập đệ quy tất cả categoryId (bao gồm cả categoryId mục tiêu và các danh mục con cháu)
        java.util.Set<UUID> categoryIds = new java.util.HashSet<>();
        collectDescendantIds(categoryId, childrenMap, categoryIds);

        // Lấy danh sách danh mục cùng với sản phẩm của chúng
        List<ProductCategory> categoriesWithProducts = productCategoryRepository.findCategoriesWithProductsByIds(categoryIds);

        // Lấy tất cả sản phẩm không trùng lặp và không bị xóa
        List<ProductListingView> listingViews = categoriesWithProducts.stream()
                .flatMap(c -> c.getProductList().stream())
                .filter(Objects::nonNull)
                .filter(p -> !p.isDeleted())
                .collect(Collectors.toMap(
                        Product::getId,
                        p -> p,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .map(this::buildListingView)
                .filter(view -> matchesSearch(view, request.getSearch())) // Lọc theo keyword
                .filter(view -> matchesMinRating(view, request.getMinRating())) // Lọc theo rating
                .sorted(buildComparator(resolveSortOption(request.getSort()))) // Sort
                .toList();

        return buildPagedResponse(listingViews, request.getPage(), request.getSize());
    }

    /**
     * Thu thập đệ quy id của danh mục hiện tại và toàn bộ danh mục con.
     *
     * @param currentId id danh mục đang duyệt
     * @param childrenMap map danh mục con theo id cha
     * @param result tập id dùng để gom kết quả
     */
    private void collectDescendantIds(
            UUID currentId,
            Map<UUID, List<ProductCategory>> childrenMap,
            java.util.Set<UUID> result) {
        result.add(currentId);
        List<ProductCategory> children = childrenMap.get(currentId);
        if (children != null) {
            for (ProductCategory child : children) {
                collectDescendantIds(child.getId(), childrenMap, result);
            }
        }
    }

    /**
     * Chọn kiểu sắp xếp mặc định nếu request không truyền sort.
     *
     * @param sortOption kiểu sắp xếp từ request
     * @return sortOption hợp lệ, mặc định là NEWEST
     */
    private CategoryProductSortOption resolveSortOption(CategoryProductSortOption sortOption) {
        return sortOption == null ? CategoryProductSortOption.NEWEST : sortOption;
    }

    /**
     * Dựng dữ liệu trung gian để hiển thị một sản phẩm trong listing.
     *
     * @param product sản phẩm cần hiển thị
     * @return view gồm ảnh đại diện, giá, rating và tồn kho
     */
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

    /**
     * Lấy biến thể đại diện của sản phẩm.
     *
     * @param product sản phẩm cần lấy biến thể
     * @return biến thể có id nhỏ nhất nếu tồn tại
     */
    private Optional<ProductVariant> getRepresentativeVariant(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return Optional.empty();
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * Lấy URL ảnh đại diện của sản phẩm.
     *
     * @param product sản phẩm cần lấy ảnh
     * @return presigned URL ảnh đại diện, hoặc null nếu sản phẩm chưa có ảnh
     */
    private String getRepresentativeImageUrl(Product product) {
        String representativeImageKey = getRepresentativeImageKey(product);
        if (representativeImageKey == null) {
            return null;
        }

        return r2StorageService.getPresignedGetUrl(representativeImageKey);
    }

    /**
     * Lấy key ảnh đại diện của sản phẩm.
     *
     * @param product sản phẩm cần lấy image key
     * @return representativeImageKey nếu có, ngược lại là ảnh đầu tiên trong gallery
     */
    private String getRepresentativeImageKey(Product product) {
        if (product.getRepresentativeImageKey() != null && !product.getRepresentativeImageKey().isBlank()) {
            return product.getRepresentativeImageKey();
        }

        if (product.getImageKeys() == null || product.getImageKeys().isEmpty()) {
            return null;
        }

        return product.getImageKeys().getFirst();
    }

    /**
     * Tính điểm đánh giá trung bình của sản phẩm.
     *
     * @param product sản phẩm cần tính rating
     * @return điểm trung bình, hoặc null nếu chưa có đánh giá
     */
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

    /**
     * Kiểm tra sản phẩm có khớp từ khóa tìm kiếm hay không.
     *
     * @param view dữ liệu listing của sản phẩm
     * @param search từ khóa tìm kiếm
     * @return true nếu không có search hoặc tên sản phẩm chứa từ khóa
     */
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

    /**
     * Kiểm tra sản phẩm có đạt mức rating tối thiểu hay không.
     *
     * @param view dữ liệu listing của sản phẩm
     * @param minRating rating tối thiểu cần lọc
     * @return true nếu không lọc rating hoặc sản phẩm đạt rating yêu cầu
     */
    private boolean matchesMinRating(ProductListingView view, Integer minRating) {
        if (minRating == null) {
            return true;
        }

        return view.averageRating() != null && view.averageRating() >= minRating;
    }

    /**
     * Tạo comparator sắp xếp danh sách sản phẩm.
     *
     * @param sortOption kiểu sắp xếp theo giá, rating hoặc thời gian tạo
     * @return comparator có fallback theo productId để thứ tự ổn định
     */
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
                    .comparingDouble(ProductCategoryService::ratingSortValue)
                    .thenComparing(productIdComparator);
            case RATING_DESC -> Comparator
                    .comparingDouble(ProductCategoryService::ratingSortValue)
                    .reversed()
                    .thenComparing(productIdComparator);
            case OLDEST -> Comparator
                    .comparing(ProductListingView::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(productIdComparator);
            case NEWEST -> Comparator
                    .comparing(ProductListingView::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(productIdComparator);
        };
    }

    private static double ratingSortValue(ProductListingView view) {
        return view.averageRating() == null ? 0.0 : view.averageRating();
    }

    /**
     * Cắt danh sách sản phẩm theo trang và map sang response trả về client.
     *
     * @param listingViews danh sách sản phẩm đã lọc và sắp xếp
     * @param page trang hiện tại, bắt đầu từ 0
     * @param size số sản phẩm mỗi trang
     * @return response phân trang cho danh sách sản phẩm
     */
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

    /**
     * View trung gian chứa dữ liệu đã tính toán cho product listing.
     *
     * @param product entity sản phẩm gốc
     * @param imageUrl URL ảnh đại diện
     * @param originalPrice giá gốc của biến thể đại diện
     * @param salePrice giá khuyến mãi của biến thể đại diện
     * @param effectivePrice giá dùng để hiển thị và sort
     * @param averageRating điểm đánh giá trung bình
     * @param stockQuantity tổng tồn kho của các biến thể chưa xóa
     */
    private record ProductListingView(
            Product product,
            String imageUrl,
            Double originalPrice,
            Double salePrice,
            Double effectivePrice,
            Double averageRating,
            Integer stockQuantity) {
        /**
         * Lấy id sản phẩm để dùng làm tie-breaker khi sort.
         *
         * @return id của sản phẩm gốc
         */
        private UUID productId() {
            return product.getId();
        }

        /**
         * Lấy thời điểm tạo sản phẩm để sort mới/cũ.
         *
         * @return thời điểm tạo của sản phẩm gốc
         */
        private Instant createdAt() {
            return product.getCreatedAt();
        }
    }

    /**
     * Đếm tổng số danh mục trong hệ thống.
     *
     * @return tổng số danh mục hiện có
     */
    @Transactional(readOnly = true)
    public long count() {
        return productCategoryRepository.count();
    }
}

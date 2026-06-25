package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ProductCreateRequest;
import hcmute.edu.zentech.dto.request.ProductUpdateRequest;
import hcmute.edu.zentech.dto.request.ProductVariantUpsertRequest;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.ProductCategorySummaryResponse;
import hcmute.edu.zentech.dto.response.ProductManagementDetailResponse;
import hcmute.edu.zentech.dto.response.ProductManagementSummaryResponse;
import hcmute.edu.zentech.dto.response.ProductVariantManagementResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import hcmute.edu.zentech.event.ProductSyncEvent;
import hcmute.edu.zentech.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductManagementService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "createdAt,desc";

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductGroupRepository productGroupRepository;
    private final ProductMapper productMapper;
    private final R2StorageService r2StorageService;
    private final ApplicationEventPublisher eventPublisher;

    public PageResponse<ProductManagementSummaryResponse> getProducts(
            int page,
            int size,
            String sort,
            String keyword,
            boolean includeDeleted) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildSort(sort));
        Page<Product> productPage = productRepository.searchManagementProducts(
                normalizeKeyword(keyword),
                includeDeleted,
                pageable
        );

        List<ProductManagementSummaryResponse> content = productPage.getContent().stream()
                .map(this::toSummaryResponse)
                .toList();

        return PageResponse.from(productPage, content);
    }

    public ProductManagementDetailResponse getProductDetail(UUID productId) {
        return toDetailResponse(getProduct(productId));
    }

    @Transactional
    public ProductManagementDetailResponse createProduct(ProductCreateRequest request) {
        String productName = requireText(request.getProductName(), "productName is required");
        ensureUniqueProductName(productName, null);

        Product product = new Product();
        product.setProductName(productName);
        product.setCategories(new HashSet<>(getCategories(request.getCategoryIds(), true)));
        product.setProductGroup(getActiveGroupOrNull(request.getProductGroupId()));
        product.setImageKeys(new ArrayList<>(normalizeImageKeys(request.getImageKeys())));
        product.setRepresentativeImageKey(resolveRepresentativeImageKey(
                request.getRepresentativeImageKey(),
                product.getImageKeys(),
                false
        ));
        applyContent(product, request);
        product.setVariants(new HashSet<>(buildNewVariants(product, request.getVariants())));

        Product savedProduct = productRepository.save(product);
        eventPublisher.publishEvent(new ProductSyncEvent(this, savedProduct.getId(), "CREATE"));
        return toDetailResponse(savedProduct);
    }

    @Transactional
    public ProductManagementDetailResponse updateProduct(UUID productId, ProductUpdateRequest request) {
        Product product = getProduct(productId);

        if (request.getProductName() != null) {
            String productName = requireText(request.getProductName(), "productName must not be blank");
            ensureUniqueProductName(productName, productId);
            product.setProductName(productName);
        }

        if (request.getCategoryIds() != null) {
            product.setCategories(new HashSet<>(getCategories(request.getCategoryIds(), true)));
        }

        if (Boolean.TRUE.equals(request.getClearProductGroup())) {
            product.setProductGroup(null);
        } else if (request.getProductGroupId() != null) {
            product.setProductGroup(getActiveGroupOrNull(request.getProductGroupId()));
        }

        if (request.getImageKeys() != null) {
            product.setImageKeys(new ArrayList<>(normalizeImageKeys(request.getImageKeys())));
        }

        if (Boolean.TRUE.equals(request.getClearRepresentativeImage())) {
            product.setRepresentativeImageKey(null);
        } else if (request.getRepresentativeImageKey() != null || request.getImageKeys() != null) {
            product.setRepresentativeImageKey(resolveRepresentativeImageKey(
                    request.getRepresentativeImageKey(),
                    product.getImageKeys(),
                    request.getRepresentativeImageKey() == null
            ));
        }

        applyContent(product, request);

        if (request.getVariants() != null) {
            upsertVariants(product, request.getVariants());
        }

        eventPublisher.publishEvent(new ProductSyncEvent(this, product.getId(), "UPDATE"));
        return toDetailResponse(product);
    }

    @Transactional
    public ProductManagementDetailResponse deleteProduct(UUID productId) {
        Product product = getProduct(productId);
        if (!product.isDeleted()) {
            Instant now = Instant.now();
            product.setDeleted(true);
            product.setDeletedAt(now);
            if (product.getVariants() != null) {
                product.getVariants().stream()
                        .filter(Objects::nonNull)
                        .filter(variant -> !variant.isDeleted())
                        .forEach(variant -> softDeleteVariant(variant, now));
            }
            eventPublisher.publishEvent(new ProductSyncEvent(this, product.getId(), "DELETE"));
        }
        return toDetailResponse(product);
    }

    private ProductManagementSummaryResponse toSummaryResponse(Product product) {
        return productMapper.toProductManagementSummaryResponse(
                product,
                getRepresentativeImageUrl(product),
                getCategoryResponses(product),
                countActiveVariants(product)
        );
    }

    private ProductManagementDetailResponse toDetailResponse(Product product) {
        return productMapper.toProductManagementDetailResponse(
                product,
                getRepresentativeImageUrl(product),
                getProductImageUrls(product),
                productMapper.toProductGroupResponse(product.getProductGroup()),
                getCategoryResponses(product),
                getVariantResponses(product)
        );
    }

    private Product getProduct(UUID productId) {
        return productRepository.findManagementDetailById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }

    private ProductGroup getActiveGroupOrNull(UUID groupId) {
        if (groupId == null) {
            return null;
        }

        return productGroupRepository.findByIdAndDeletedFalse(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Group", "id", groupId));
    }

    private List<ProductCategory> getCategories(List<UUID> categoryIds, boolean required) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            if (required) {
                throw new RuntimeException("categoryIds is required");
            }
            return List.of();
        }

        List<UUID> uniqueIds = categoryIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            throw new RuntimeException("categoryIds is required");
        }

        List<ProductCategory> categories = categoryRepository.findAllById(uniqueIds);
        if (categories.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("Product Category", "id", uniqueIds);
        }

        return categories;
    }

    private List<ProductVariant> buildNewVariants(Product product, List<ProductVariantUpsertRequest> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        return variants.stream()
                .map(request -> buildNewVariant(product, request))
                .toList();
    }

    private ProductVariant buildNewVariant(Product product, ProductVariantUpsertRequest request) {
        if (request.getId() != null) {
            throw new RuntimeException("variant id must be empty when creating product");
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        applyVariantFields(variant, request, true);
        return variant;
    }

    private void upsertVariants(Product product, List<ProductVariantUpsertRequest> variantRequests) {
        Map<UUID, ProductVariant> variantsById = product.getVariants() == null
                ? new LinkedHashMap<>()
                : product.getVariants().stream()
                        .filter(variant -> variant.getId() != null)
                        .collect(Collectors.toMap(
                                ProductVariant::getId,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));

        Set<UUID> retainedVariantIds = new HashSet<>();
        Set<ProductVariant> managedVariants = product.getVariants() == null
                ? new HashSet<>()
                : product.getVariants();

        for (ProductVariantUpsertRequest request : variantRequests) {
            if (request.getId() == null) {
                managedVariants.add(buildNewVariant(product, request));
                continue;
            }

            ProductVariant variant = variantsById.get(request.getId());
            if (variant == null) {
                throw new ResourceNotFoundException("Product Variant", "id", request.getId());
            }

            applyVariantFields(variant, request, false);
            variant.setDeleted(false);
            variant.setDeletedAt(null);
            retainedVariantIds.add(variant.getId());
        }

        Instant now = Instant.now();
        managedVariants.stream()
                .filter(variant -> variant.getId() != null)
                .filter(variant -> !retainedVariantIds.contains(variant.getId()))
                .filter(variant -> !variant.isDeleted())
                .forEach(variant -> softDeleteVariant(variant, now));

        product.setVariants(managedVariants);
    }

    private void applyVariantFields(ProductVariant variant, ProductVariantUpsertRequest request, boolean creating) {
        if (creating && request.getOriginalPrice() == null) {
            throw new RuntimeException("variant originalPrice is required");
        }
        if (creating && request.getStockQuantity() == null) {
            throw new RuntimeException("variant stockQuantity is required");
        }

        if (request.getOriginalPrice() != null) {
            variant.setOriginalPrice(request.getOriginalPrice());
        }
        if (request.getName() != null) {
            variant.setName(normalizeText(request.getName()));
        }
        if (request.getNameColor() != null) {
            variant.setNameColor(normalizeText(request.getNameColor()));
        }
        if (request.getColorCode() != null) {
            variant.setColorCode(normalizeText(request.getColorCode()));
        }
        if (request.getSaleStartAt() != null) {
            variant.setSaleStartAt(request.getSaleStartAt());
        }
        if (request.getSaleEndAt() != null) {
            variant.setSaleEndAt(request.getSaleEndAt());
        }
        if (request.getSalePrice() != null) {
            variant.setSalePrice(resolveSalePrice(variant, request.getSalePrice()));
        }
        if (request.getStockQuantity() != null) {
            variant.setStockQuantity(request.getStockQuantity());
        }
    }

    private Double resolveSalePrice(ProductVariant variant, Double salePrice) {
        if (variant.getSaleStartAt() == null && variant.getSaleEndAt() == null) {
            return null;
        }
        return salePrice;
    }

    private void softDeleteVariant(ProductVariant variant, Instant deletedAt) {
        variant.setDeleted(true);
        variant.setDeletedAt(deletedAt);
    }

    private void ensureUniqueProductName(String productName, UUID productId) {
        if (productRepository.existsActiveProductNameExcludingId(productName, productId)) {
            throw new RuntimeException("Product name already exists");
        }
    }

    private void applyContent(Product product, ProductCreateRequest request) {
        product.setSpecifications(normalizeText(request.getSpecifications()));
        product.setCompatibility(normalizeText(request.getCompatibility()));
        product.setBoxContents(normalizeText(request.getBoxContents()));
        product.setSupportInfo(normalizeText(request.getSupportInfo()));
    }

    private void applyContent(Product product, ProductUpdateRequest request) {
        if (request.getSpecifications() != null) {
            product.setSpecifications(normalizeText(request.getSpecifications()));
        }
        if (request.getCompatibility() != null) {
            product.setCompatibility(normalizeText(request.getCompatibility()));
        }
        if (request.getBoxContents() != null) {
            product.setBoxContents(normalizeText(request.getBoxContents()));
        }
        if (request.getSupportInfo() != null) {
            product.setSupportInfo(normalizeText(request.getSupportInfo()));
        }
    }

    private List<String> normalizeImageKeys(List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return List.of();
        }

        return imageKeys.stream()
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String resolveRepresentativeImageKey(String requestedKey, List<String> imageKeys, boolean fallbackToFirst) {
        String normalizedKey = normalizeText(requestedKey);
        if (normalizedKey != null) {
            return normalizedKey;
        }

        if (fallbackToFirst && imageKeys != null && !imageKeys.isEmpty()) {
            return imageKeys.getFirst();
        }

        return null;
    }

    private String getRepresentativeImageUrl(Product product) {
        String imageKey = getRepresentativeImageKey(product);
        if (imageKey == null) {
            return null;
        }
        return r2StorageService.getPresignedGetUrl(imageKey);
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

    private List<ProductCategorySummaryResponse> getCategoryResponses(Product product) {
        if (product.getCategories() == null || product.getCategories().isEmpty()) {
            return List.of();
        }

        return product.getCategories().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(ProductCategory::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductCategory::getCategoryName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ProductCategory::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(category -> productMapper.toProductCategorySummaryResponse(category, false, List.of()))
                .toList();
    }

    private List<ProductVariantManagementResponse> getVariantResponses(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return List.of();
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(productMapper::toProductVariantManagementResponse)
                .toList();
    }

    private int countActiveVariants(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0;
        }

        return (int) product.getVariants().stream()
                .filter(Objects::nonNull)
                .filter(variant -> !variant.isDeleted())
                .count();
    }

    private String requireText(String value, String message) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private String normalizeKeyword(String keyword) {
        return normalizeText(keyword);
    }

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Sort buildSort(String sort) {
        String sortValue = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "asc";

        Map<String, String> sortableFields = Map.of(
                "createdAt", "createdAt",
                "updatedAt", "updatedAt",
                "productName", "productName",
                "deletedAt", "deletedAt"
        );
        String mappedField = sortableFields.getOrDefault(requestedField, "createdAt");
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, mappedField), new Sort.Order(Sort.Direction.ASC, "id"));
    }
}

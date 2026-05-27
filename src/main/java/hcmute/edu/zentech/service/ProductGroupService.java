package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ProductGroupCreateRequest;
import hcmute.edu.zentech.dto.request.ProductGroupUpdateRequest;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.ProductGroupResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductGroupService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "groupName,asc";

    private final ProductGroupRepository productGroupRepository;
    private final ProductMapper productMapper;
    private final ProductRepository productRepository;

    @Transactional
    public ProductGroup getOrCreateGroup(String groupName, String description) {
        return productGroupRepository.findByGroupNameAndDeletedFalse(groupName)
                .orElseGet(() -> {
                    ProductGroup newGroup = ProductGroup.builder()
                            .groupName(groupName)
                            .description(description)
                            .build();
                    return productGroupRepository.save(newGroup);
                });
    }

    public PageResponse<ProductGroupResponse> getGroups(
            int page,
            int size,
            String sort,
            String keyword,
            boolean includeDeleted) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildSort(sort));
        Page<ProductGroup> groupPage = productGroupRepository.searchManagementGroups(
                normalizeKeyword(keyword),
                includeDeleted,
                pageable
        );
        List<ProductGroupResponse> content = groupPage.getContent().stream()
                .map(productMapper::toProductGroupResponse)
                .toList();

        return PageResponse.from(groupPage, content);
    }

    public ProductGroupResponse getGroupDetail(UUID groupId) {
        ProductGroup group = getGroup(groupId);
        return productMapper.toProductGroupResponse(group);
    }

    @Transactional
    public ProductGroupResponse createGroup(ProductGroupCreateRequest request) {
        String groupName = requireText(request.getGroupName(), "groupName is required");
        ensureUniqueGroupName(groupName, null);

        ProductGroup group = ProductGroup.builder()
                .groupName(groupName)
                .description(normalizeText(request.getDescription()))
                .build();

        ProductGroup savedGroup = productGroupRepository.save(group);

        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            List<Product> products = productRepository.findAllById(request.getProductIds());
            for (Product product : products) {
                product.setProductGroup(savedGroup);
            }
            productRepository.saveAll(products);
            savedGroup.setProducts(new HashSet<>(products));
        }

        return productMapper.toProductGroupResponse(savedGroup);
    }

    @Transactional
    public ProductGroupResponse updateGroup(UUID groupId, ProductGroupUpdateRequest request) {
        ProductGroup group = getGroup(groupId);

        if (request.getGroupName() != null) {
            String groupName = requireText(request.getGroupName(), "groupName must not be blank");
            ensureUniqueGroupName(groupName, groupId);
            group.setGroupName(groupName);
        }

        if (request.getDescription() != null) {
            group.setDescription(normalizeText(request.getDescription()));
        }

        if (request.getProductIds() != null) {
            Set<Product> oldProducts = group.getProducts();
            Set<UUID> newProductIds = new HashSet<>(request.getProductIds());

            if (oldProducts != null) {
                for (Product oldProduct : oldProducts) {
                    if (!newProductIds.contains(oldProduct.getId())) {
                        oldProduct.setProductGroup(null);
                    }
                }
            }

            List<Product> newProducts = productRepository.findAllById(request.getProductIds());
            for (Product newProduct : newProducts) {
                newProduct.setProductGroup(group);
            }

            productRepository.saveAll(newProducts);
            group.setProducts(new HashSet<>(newProducts));
        }

        return productMapper.toProductGroupResponse(group);
    }

    @Transactional
    public ProductGroupResponse deleteGroup(UUID groupId) {
        ProductGroup group = getGroup(groupId);
        if (!group.isDeleted()) {
            group.setDeleted(true);
            group.setDeletedAt(Instant.now());
        }
        return productMapper.toProductGroupResponse(group);
    }

    @Transactional(readOnly = true)
    public boolean existsByGroupName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return false;
        }
        return productGroupRepository.existsActiveGroupNameExcludingId(groupName.trim(), null);
    }

    private ProductGroup getGroup(UUID groupId) {
        return productGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Group", "id", groupId));
    }

    private void ensureUniqueGroupName(String groupName, UUID groupId) {
        if (productGroupRepository.existsActiveGroupNameExcludingId(groupName, groupId)) {
            throw new RuntimeException("Product group name already exists");
        }
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
                "groupName", "groupName",
                "updatedAt", "updatedAt",
                "deletedAt", "deletedAt"
        );
        String mappedField = sortableFields.getOrDefault(requestedField, "groupName");
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, mappedField), new Sort.Order(Sort.Direction.ASC, "id"));
    }
}

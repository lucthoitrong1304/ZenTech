package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.InventoryAdjustmentRequest;
import hcmute.edu.zentech.dto.response.InventorySummaryResponse;
import hcmute.edu.zentech.dto.response.InventoryTransactionResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.InventoryTransaction;
import hcmute.edu.zentech.model.InventoryTransactionReason;
import hcmute.edu.zentech.model.InventoryTransactionType;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.InventoryTransactionRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.projection.TransactionStatsProjection;
import hcmute.edu.zentech.dto.response.InventoryTransactionStatsResponse;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import hcmute.edu.zentech.dto.request.AiInventoryItemDto;
import hcmute.edu.zentech.dto.request.AiInventoryRecommendRequest;
import hcmute.edu.zentech.dto.response.AiInventoryRecommendResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryManagementService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "product.productName,asc";

    private final ProductVariantRepository productVariantRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountUserRepository accountUserRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final R2StorageService r2StorageService;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryManagementService.class);

    public PageResponse<InventorySummaryResponse> getInventorySummary(
            int page,
            int size,
            String sort,
            String keyword,
            String stockStatus
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildSummarySort(sort));
        String status = (stockStatus == null || stockStatus.isBlank()) ? "all" : stockStatus.trim().toLowerCase();
        
        Page<ProductVariant> variantPage = productVariantRepository.searchInventory(
                normalizeText(keyword),
                status,
                pageable
        );

        List<InventorySummaryResponse> content = variantPage.getContent().stream()
                .map(this::toSummaryResponse)
                .toList();

        return PageResponse.from(variantPage, content);
    }

    public PageResponse<InventoryTransactionResponse> getTransactionLogs(
            int page,
            int size,
            String sort,
            String keyword,
            String type,
            UUID employeeId,
            String reason,
            Instant startDate,
            Instant endDate
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildLogSort(sort));
        InventoryTransactionType txType = parseTransactionType(type);
        InventoryTransactionReason txReason = parseTransactionReason(reason);

        Page<InventoryTransaction> txPage = inventoryTransactionRepository.searchTransactions(
                normalizeText(keyword),
                txType,
                employeeId,
                txReason,
                startDate,
                endDate,
                pageable
        );

        // Fetch employee details in one query for optimization
        List<UUID> creatorIds = txPage.getContent().stream()
                .map(InventoryTransaction::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Employee> employeeMap = new HashMap<>();
        Map<UUID, AccountUser> accountMap = new HashMap<>();
        if (!creatorIds.isEmpty()) {
            List<Employee> employees = employeeRepository.findByUserInfo_IdIn(creatorIds);
            for (Employee emp : employees) {
                if (emp.getUserInfo() != null) {
                    employeeMap.put(emp.getUserInfo().getId(), emp);
                }
            }
            accountUserRepository.findAllById(creatorIds).forEach(account -> accountMap.put(account.getId(), account));
        }

        List<InventoryTransactionResponse> content = txPage.getContent().stream()
                .map(tx -> toTransactionResponse(tx, employeeMap, accountMap))
                .toList();

        return PageResponse.from(txPage, content);
    }

    public InventoryTransactionStatsResponse getTransactionStats(
            String keyword,
            String type,
            UUID employeeId,
            String reason,
            Instant startDate,
            Instant endDate
    ) {
        InventoryTransactionType txType = parseTransactionType(type);
        InventoryTransactionReason txReason = parseTransactionReason(reason);

        TransactionStatsProjection projection = inventoryTransactionRepository.getTransactionStats(
                normalizeText(keyword),
                txType,
                employeeId,
                txReason,
                startDate,
                endDate
        );

        return InventoryTransactionStatsResponse.builder()
                .totalImports(projection.getTotalImports() != null ? projection.getTotalImports() : 0L)
                .totalExports(projection.getTotalExports() != null ? projection.getTotalExports() : 0L)
                .totalCount(projection.getTotalCount() != null ? projection.getTotalCount() : 0L)
                .build();
    }

    @Transactional
    public InventoryTransactionResponse adjustStock(InventoryAdjustmentRequest request, UUID employeeId) {
        ProductVariant variant = productVariantRepository.findById(request.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product Variant", "id", request.getProductVariantId()));

        if (variant.isDeleted()) {
            throw new IllegalArgumentException("Cannot adjust stock for deleted variant");
        }

        int adjustmentQty = request.getQuantity();
        String targetWarehouse = request.getTargetWarehouse() != null ? request.getTargetWarehouse().toUpperCase() : "MAIN";

        if ("FAULTY".equals(targetWarehouse)) {
            if (request.getType() == InventoryTransactionType.IMPORT) {
                variant.setFaultyQuantity(variant.getFaultyQuantity() + adjustmentQty);
                if (request.getReason() == InventoryTransactionReason.DAMAGED || 
                    request.getReason() == InventoryTransactionReason.ADJUSTMENT_SUB || 
                    request.getReason() == InventoryTransactionReason.CUSTOMER_ORDER) {
                    throw new IllegalArgumentException("Invalid reason for IMPORT transaction");
                }
            } else {
                int newFaulty = variant.getFaultyQuantity() - adjustmentQty;
                if (newFaulty < 0) {
                    throw new IllegalArgumentException("Insufficient faulty stock. Current faulty stock is " + variant.getFaultyQuantity());
                }
                variant.setFaultyQuantity(newFaulty);
                if (request.getReason() == InventoryTransactionReason.NEW_STOCK || 
                    request.getReason() == InventoryTransactionReason.ADJUSTMENT_ADD || 
                    request.getReason() == InventoryTransactionReason.RETURN) {
                    throw new IllegalArgumentException("Invalid reason for EXPORT transaction");
                }
            }
        } else {
            if (request.getType() == InventoryTransactionType.IMPORT) {
                variant.setStockQuantity(variant.getStockQuantity() + adjustmentQty);
                if (request.getReason() == InventoryTransactionReason.DAMAGED || 
                    request.getReason() == InventoryTransactionReason.ADJUSTMENT_SUB || 
                    request.getReason() == InventoryTransactionReason.CUSTOMER_ORDER) {
                    throw new IllegalArgumentException("Invalid reason for IMPORT transaction");
                }
            } else {
                int newStock = variant.getStockQuantity() - adjustmentQty;
                if (newStock < 0) {
                    throw new IllegalArgumentException("Insufficient stock. Current stock is " + variant.getStockQuantity());
                }
                variant.setStockQuantity(newStock);
                if (request.getReason() == InventoryTransactionReason.NEW_STOCK || 
                    request.getReason() == InventoryTransactionReason.ADJUSTMENT_ADD || 
                    request.getReason() == InventoryTransactionReason.RETURN) {
                    throw new IllegalArgumentException("Invalid reason for EXPORT transaction");
                }
                if (request.getReason() == InventoryTransactionReason.DAMAGED) {
                    variant.setFaultyQuantity(variant.getFaultyQuantity() + adjustmentQty);
                }
            }
        }

        productVariantRepository.save(variant);

        String noteSuffix = "FAULTY".equals(targetWarehouse) ? " (Kho lỗi)" : "";
        String finalNote = request.getNote() != null ? request.getNote() + noteSuffix : noteSuffix;

        InventoryTransaction transaction = InventoryTransaction.builder()
                .productVariant(variant)
                .type(request.getType())
                .quantity(adjustmentQty)
                .reason(request.getReason())
                .note(finalNote.trim())
                .createdBy(employeeId)
                .targetWarehouse(targetWarehouse)
                .build();

        InventoryTransaction savedTx = inventoryTransactionRepository.save(transaction);
        return toTransactionResponse(savedTx);
    }

    public hcmute.edu.zentech.dto.response.InventoryStatsResponse getInventoryStats() {
        long total = productVariantRepository.countActiveVariants();
        long low = productVariantRepository.countLowStockVariants();
        long out = productVariantRepository.countOutOfStockVariants();
        long faultyVars = productVariantRepository.countFaultyVariants();
        Long faultyQtySum = productVariantRepository.sumFaultyQuantity();
        long faultyQty = faultyQtySum != null ? faultyQtySum : 0;
        long highFaulty = productVariantRepository.countHighFaultyAlertVariants();

        return hcmute.edu.zentech.dto.response.InventoryStatsResponse.builder()
                .totalItems(total)
                .lowStockCount(low)
                .outOfStockCount(out)
                .totalFaultyVariants(faultyVars)
                .totalFaultyQuantity(faultyQty)
                .highFaultyAlertCount(highFaulty)
                .build();
    }

    public AiInventoryRecommendResponse getAiRecommendations() {
        List<ProductVariant> lowStockVariants = productVariantRepository.findLowStockAndOutOfStockVariants();
        
        List<AiInventoryItemDto> itemDtos = new java.util.ArrayList<>();
        java.time.Instant startDate = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);

        for (ProductVariant variant : lowStockVariants) {
            long totalSold = inventoryTransactionRepository.sumQuantityByVariantAndReasonAndDate(
                    variant.getId(),
                    InventoryTransactionReason.CUSTOMER_ORDER,
                    startDate
            );
            
            double averageWeeklySales = totalSold * (7.0 / 30.0);
            int currentStock = variant.getStockQuantity();
            int suggestedQty = Math.max(10, (int) Math.round((averageWeeklySales * 2.0) - currentStock));

            itemDtos.add(AiInventoryItemDto.builder()
                    .productName(variant.getProduct().getProductName())
                    .variantName(variant.getName())
                    .currentStock(currentStock)
                    .averageWeeklySales(averageWeeklySales)
                    .suggestedQty(suggestedQty)
                    .build());
        }

        String url = aiBaseUrl + "/management/inventory/recommend";
        AiInventoryRecommendRequest requestPayload = AiInventoryRecommendRequest.builder()
                .items(itemDtos)
                .build();

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            addTraceIdHeader(headers);
            org.springframework.http.HttpEntity<AiInventoryRecommendRequest> entity = new org.springframework.http.HttpEntity<>(requestPayload, headers);
            
            org.springframework.http.ResponseEntity<AiInventoryRecommendResponse> response = restTemplate.postForEntity(
                    url, entity, AiInventoryRecommendResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to fetch AI inventory recommendations: {}", e.getMessage());
            return AiInventoryRecommendResponse.builder()
                    .content("### 🚨 Lỗi kết nối hệ thống AI\n\nKhông thể kết nối đến máy chủ AI ZenTech để phân tích dữ liệu kho. Vui lòng kiểm tra lại dịch vụ AI hoặc thử lại sau.")
                    .build();
        }

        return AiInventoryRecommendResponse.builder()
                .content("### ⚠️ Phản hồi trống từ AI\n\nHệ thống AI không phản hồi kết quả phân tích. Vui lòng thử lại sau.")
                .build();
    }


    private void addTraceIdHeader(org.springframework.http.HttpHeaders headers) {
        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId.trim());
        }
    }

    private InventorySummaryResponse toSummaryResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        return InventorySummaryResponse.builder()
                .variantId(variant.getId())
                .productId(product.getId())
                .productName(product.getProductName())
                .variantName(variant.getName())
                .colorCode(variant.getColorCode())
                .originalPrice(variant.getOriginalPrice())
                .salePrice(variant.getSalePrice())
                .stockQuantity(variant.getStockQuantity())
                .faultyQuantity(variant.getFaultyQuantity())
                .representativeImageUrl(getRepresentativeImageUrl(product))
                .build();
    }

    private InventoryTransactionResponse toTransactionResponse(InventoryTransaction transaction) {
        Map<UUID, Employee> employeeMap = new HashMap<>();
        Map<UUID, AccountUser> accountMap = new HashMap<>();
        if (transaction.getCreatedBy() != null) {
            employeeRepository.findByUserInfo_Id(transaction.getCreatedBy()).ifPresent(emp -> {
                employeeMap.put(transaction.getCreatedBy(), emp);
            });
            accountUserRepository.findById(transaction.getCreatedBy()).ifPresent(account -> {
                accountMap.put(transaction.getCreatedBy(), account);
            });
        }
        return toTransactionResponse(transaction, employeeMap, accountMap);
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }

    private InventoryTransactionResponse toTransactionResponse(
            InventoryTransaction transaction,
            Map<UUID, Employee> employeeMap,
            Map<UUID, AccountUser> accountMap
    ) {
        ProductVariant variant = transaction.getProductVariant();
        Product product = variant.getProduct();

        String createdByName = "Hệ thống";
        String createdByEmail = null;
        String createdByAvatar = null;

        if (transaction.getCreatedBy() != null) {
            Employee emp = employeeMap.get(transaction.getCreatedBy());
            AccountUser account = accountMap.get(transaction.getCreatedBy());
            Customer customer = customerRepository.findByUserInfo_Id(transaction.getCreatedBy()).orElse(null);

            if (emp != null) {
                createdByName = emp.getFullName();
                createdByEmail = emp.getUserInfo() != null ? emp.getUserInfo().getEmail() : null;
                createdByAvatar = resolveImageUrl(emp.getImageUrl());
            } else if (customer != null) {
                createdByName = customer.getFullName();
                createdByEmail = customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : null;
                createdByAvatar = resolveImageUrl(customer.getImageUrl());
            } else if (account != null) {
                createdByEmail = account.getEmail();
                createdByName = account.getEmail();
            } else {
                createdByName = "Tài khoản không xác định";
            }
        } else if (transaction.getReason() == InventoryTransactionReason.CUSTOMER_ORDER || 
                   transaction.getReason() == InventoryTransactionReason.RETURN) {
            Customer customer = null;
            if (transaction.getNote() != null) {
                int hashIndex = transaction.getNote().lastIndexOf('#');
                if (hashIndex != -1 && hashIndex < transaction.getNote().length() - 1) {
                    try {
                        String orderIdStr = transaction.getNote().substring(hashIndex + 1).trim();
                        UUID orderId = UUID.fromString(orderIdStr);
                        Order order = orderRepository.findById(orderId).orElse(null);
                        if (order != null) {
                            customer = order.getCustomer();
                        }
                    } catch (IllegalArgumentException e) {
                        // ignore malformed UUID
                    }
                }
            }
            if (customer != null) {
                createdByName = customer.getFullName();
                createdByEmail = customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : null;
                createdByAvatar = resolveImageUrl(customer.getImageUrl());
            }
        }

        return InventoryTransactionResponse.builder()
                .id(transaction.getId())
                .productName(product.getProductName())
                .variantName(variant.getName())
                .type(transaction.getType())
                .quantity(transaction.getQuantity())
                .reason(transaction.getReason())
                .note(transaction.getNote())
                .createdAt(transaction.getCreatedAt())
                .createdBy(transaction.getCreatedBy())
                .createdByName(createdByName)
                .createdByEmail(createdByEmail)
                .createdByAvatar(createdByAvatar)
                .targetWarehouse(transaction.getTargetWarehouse())
                .build();
    }

    private InventoryTransactionType parseTransactionType(String type) {
        if (type != null && !type.isBlank() && !"all".equalsIgnoreCase(type)) {
            try {
                return InventoryTransactionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }
        return null;
    }

    private InventoryTransactionReason parseTransactionReason(String reason) {
        if (reason != null && !reason.isBlank() && !"all".equalsIgnoreCase(reason)) {
            try {
                return InventoryTransactionReason.valueOf(reason.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore
            }
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

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Sort buildSummarySort(String sort) {
        String sortValue = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "asc";

        Map<String, String> sortableFields = Map.of(
                "productName", "product.productName",
                "variantName", "name",
                "stockQuantity", "stockQuantity",
                "originalPrice", "originalPrice"
        );
        String mappedField = sortableFields.getOrDefault(requestedField, "product.productName");
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, mappedField), new Sort.Order(Sort.Direction.ASC, "id"));
    }

    private Sort buildLogSort(String sort) {
        String sortValue = (sort == null || sort.isBlank()) ? "createdAt,desc" : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "desc";

        Map<String, String> sortableFields = Map.of(
                "createdAt", "createdAt",
                "quantity", "quantity",
                "productName", "productVariant.product.productName"
        );
        String mappedField = sortableFields.getOrDefault(requestedField, "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(directionValue) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(direction, mappedField), new Sort.Order(Sort.Direction.ASC, "id"));
    }
}

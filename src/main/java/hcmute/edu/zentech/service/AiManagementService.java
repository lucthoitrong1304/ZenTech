package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.AiAgentDemoRequest;
import hcmute.edu.zentech.dto.request.AiAgentRuntimeRequest;
import hcmute.edu.zentech.dto.request.AiDatasetRequest;
import hcmute.edu.zentech.dto.request.AiKnowledgeIngestRequest;
import hcmute.edu.zentech.dto.request.AiProductVectorVerifyRequest;
import hcmute.edu.zentech.dto.response.AiAgentDemoResponse;
import hcmute.edu.zentech.dto.response.AiAgentRuntimeResponse;
import hcmute.edu.zentech.dto.response.AiDatasetResponse;
import hcmute.edu.zentech.dto.response.AiDocumentResponse;
import hcmute.edu.zentech.dto.response.AiKnowledgeIngestResponse;
import hcmute.edu.zentech.dto.response.AiProductVectorStatusResponse;
import hcmute.edu.zentech.dto.response.AiProductVectorVerifyResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.AiDataset;
import hcmute.edu.zentech.model.AiDatasetStatus;
import hcmute.edu.zentech.model.AiDocument;
import hcmute.edu.zentech.model.AiDocumentStatus;
import hcmute.edu.zentech.model.AiProductVectorStatus;
import hcmute.edu.zentech.model.AiProductVectorSyncStatus;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AiDatasetRepository;
import hcmute.edu.zentech.repository.AiDocumentRepository;
import hcmute.edu.zentech.repository.AiProductVectorStatusRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AiManagementService {
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
    private static final UUID DEFAULT_CUSTOMER_AGENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "application/octet-stream"
    );

    private final AiDatasetRepository aiDatasetRepository;
    private final AiDocumentRepository aiDocumentRepository;
    private final AiProductVectorStatusRepository aiProductVectorStatusRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${app.ai.timeout-ms:15000}")
    private long aiTimeoutMs;

    @Value("${app.ai.default-agent.name:ZenTech Customer Assistant}")
    private String defaultAgentName;

    @Value("${app.ai.default-agent.system-prompt:Ban la tro ly tu van khach hang cua ZenTech. Hay tra loi tu nhien, ngan gon, lich su bang tieng Viet va uu tien thong tin san pham/dataset duoc cung cap.}")
    private String defaultAgentSystemPrompt;

    @Value("${app.ai.default-agent.guardrails:Khong tiet lo thong tin ky thuat noi bo. Neu thieu du lieu chinh xac, hay hoi them thong tin hoac de nghi nhan vien ho tro.}")
    private String defaultAgentGuardrails;

    @Value("${app.ai.default-agent.fallback-message:Hien tai ZenTech AI chua co du du lieu de tra loi chac chan. Ban vui long bo sung thong tin hoac doi nhan vien ho tro.}")
    private String defaultAgentFallbackMessage;

    public List<AiDatasetResponse> getDatasets() {
        return aiDatasetRepository.findAllWithDocuments().stream()
                .map(this::toDatasetResponse)
                .toList();
    }

    public AiDatasetResponse getDataset(UUID datasetId) {
        return toDatasetResponse(getDatasetEntity(datasetId));
    }

    @Transactional
    public AiDatasetResponse createDataset(AiDatasetRequest request) {
        AiDataset dataset = AiDataset.builder()
                .name(requireText(request.getName(), "Dataset name is required"))
                .description(normalizeText(request.getDescription()))
                .status(request.getStatus() == null ? AiDatasetStatus.ACTIVE : request.getStatus())
                .createdBy(SecurityContextUtils.getCurrentUserId())
                .build();
        return toDatasetResponse(aiDatasetRepository.save(dataset));
    }

    @Transactional
    public AiDatasetResponse updateDataset(UUID datasetId, AiDatasetRequest request) {
        AiDataset dataset = getDatasetEntity(datasetId);
        dataset.setName(requireText(request.getName(), "Dataset name is required"));
        dataset.setDescription(normalizeText(request.getDescription()));
        dataset.setStatus(request.getStatus() == null ? AiDatasetStatus.ACTIVE : request.getStatus());
        return toDatasetResponse(dataset);
    }

    @Transactional
    public AiDatasetResponse deleteDataset(UUID datasetId) {
        AiDataset dataset = getDatasetEntity(datasetId);
        dataset.setStatus(AiDatasetStatus.ARCHIVED);
        for (AiDocument document : dataset.getDocuments()) {
            document.setIngestStatus(AiDocumentStatus.FAILED);
            document.setErrorMessage("Dataset archived");
            deleteDocumentFromAiService(document.getId());
        }
        return toDatasetResponse(dataset);
    }

    @Transactional
    public AiDocumentResponse uploadDocument(UUID datasetId, MultipartFile file) {
        AiDataset dataset = getDatasetEntity(datasetId);
        validateDocument(file);

        try {
            AiDocument document = AiDocument.builder()
                    .dataset(dataset)
                    .fileName(requireText(file.getOriginalFilename(), "File name is required"))
                    .fileType(resolveContentType(file))
                    .fileSize(file.getSize())
                    .contentBase64(Base64.getEncoder().encodeToString(file.getBytes()))
                    .ingestStatus(AiDocumentStatus.PROCESSING)
                    .build();

            AiDocument savedDocument = aiDocumentRepository.save(document);
            ingestDocument(savedDocument);
            return toDocumentResponse(savedDocument);
        } catch (Exception ex) {
            throw new RuntimeException("Could not upload AI document: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public AiDocumentResponse reingestDocument(UUID documentId) {
        AiDocument document = getDocumentEntity(documentId);
        if (document.getContentBase64() == null || document.getContentBase64().isBlank()) {
            throw new RuntimeException("Original document content is not available for reingest");
        }
        document.setIngestStatus(AiDocumentStatus.PROCESSING);
        document.setErrorMessage(null);
        document.setChunkCount(0);
        ingestDocument(document);
        return toDocumentResponse(document);
    }

    @Transactional
    public AiDocumentResponse deleteDocument(UUID documentId) {
        AiDocument document = getDocumentEntity(documentId);
        deleteDocumentFromAiService(documentId);
        aiDocumentRepository.delete(document);
        return toDocumentResponse(document);
    }

    public List<AiProductVectorStatusResponse> getProductVectorStatuses(String filter) {
        List<Product> products = productRepository.findAll();
        List<UUID> variantIds = products.stream()
                .flatMap(product -> product.getVariants().stream())
                .map(ProductVariant::getId)
                .toList();
        Map<UUID, AiProductVectorStatus> statusByVariantId = aiProductVectorStatusRepository
                .findByVariantIdIn(variantIds)
                .stream()
                .collect(Collectors.toMap(AiProductVectorStatus::getVariantId, Function.identity()));

        String normalizedFilter = normalizeText(filter);
        return products.stream()
                .filter(product -> !product.isDeleted())
                .flatMap(product -> product.getVariants().stream()
                        .filter(variant -> !variant.isDeleted())
                        .map(variant -> toProductVectorStatusResponse(product, variant, statusByVariantId.get(variant.getId()))))
                .filter(response -> matchesProductVectorFilter(response, normalizedFilter))
                .sorted(Comparator.comparing(AiProductVectorStatusResponse::getProductName)
                        .thenComparing(response -> Optional.ofNullable(response.getVariantName()).orElse("")))
                .toList();
    }

    @Transactional
    public AiProductVectorStatusResponse syncProductVariantToAi(UUID variantId) {
        ProductVariant variant = findProductVariant(variantId);
        syncProductToAi(variant.getProduct().getId());
        AiProductVectorStatus status = aiProductVectorStatusRepository.findByVariantId(variantId).orElse(null);
        return toProductVectorStatusResponse(variant.getProduct(), variant, status);
    }

    @Transactional
    public AiProductVectorStatusResponse verifyProductVariantInQdrant(UUID variantId) {
        ProductVariant variant = findProductVariant(variantId);
        AiProductVectorStatus status = ensureVectorStatus(variant.getProduct().getId(), variantId);
        verifyStatuses(List.of(status));
        return toProductVectorStatusResponse(variant.getProduct(), variant, status);
    }

    @Transactional
    public List<AiProductVectorStatusResponse> verifyAllProductVectors() {
        List<AiProductVectorStatus> statuses = productRepository.findAll().stream()
                .filter(product -> !product.isDeleted())
                .flatMap(product -> product.getVariants().stream()
                        .filter(variant -> !variant.isDeleted())
                        .map(variant -> ensureVectorStatus(product.getId(), variant.getId())))
                .toList();
        verifyStatuses(statuses);
        return getProductVectorStatuses(null);
    }

    public Optional<String> generateRuntimeReply(
            Role role,
            String message,
            List<AiAgentRuntimeRequest.HistoryMessage> history,
            List<AiAgentRuntimeRequest.Attachment> attachments,
            Map<String, Object> businessContext
    ) {
        return requestAiReply(role, message, history, attachments, businessContext)
                .map(AiAgentRuntimeResponse::getContent)
                .map(this::normalizeText);
    }

    public Optional<java.net.http.HttpResponse<java.io.InputStream>> requestAiReplyStream(
            Role role,
            String message,
            List<AiAgentRuntimeRequest.HistoryMessage> history,
            List<AiAgentRuntimeRequest.Attachment> attachments,
            Map<String, Object> businessContext
    ) {
        try {
            AiAgentRuntimeRequest runtimeRequest = buildRuntimeRequest(role, message, history, attachments, businessContext);

            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/agents/respond/stream"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(runtimeRequest)))
                    .build();

            java.net.http.HttpResponse<java.io.InputStream> response = java.net.http.HttpClient.newBuilder()
                    .build()
                    .send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI agent stream service returned status {}", response.statusCode());
                return Optional.empty();
            }

            return Optional.of(response);
        } catch (Exception ex) {
            log.warn("AI agent stream service failed", ex);
            return Optional.empty();
        }
    }

    public AiAgentDemoResponse demoAgent(AiAgentDemoRequest request) {
        AiAgentRuntimeResponse response = requestAiReply(
                Role.CUSTOMER,
                request.getMessage(),
                request.getHistory() == null ? List.of() : request.getHistory(),
                List.of(),
                buildBusinessContext(Role.CUSTOMER)
        ).orElseGet(() -> {
            AiAgentRuntimeResponse fallback = new AiAgentRuntimeResponse();
            fallback.setContent(defaultAgentFallbackMessage);
            fallback.setFallback(true);
            return fallback;
        });

        return AiAgentDemoResponse.builder()
                .content(response.getContent())
                .fallback(response.isFallback())
                .handoffRecommended(response.isHandoffRecommended())
                .retrievedContext(response.getRetrievedContext().stream()
                        .map(item -> AiAgentDemoResponse.RetrievedContext.builder()
                                .id(item.getId())
                                .content(item.getContent())
                                .score(item.getScore())
                                .source(item.getSource())
                                .datasetId(item.getDatasetId())
                                .documentId(item.getDocumentId())
                                .build())
                        .toList())
                .build();
    }

    private Optional<AiAgentRuntimeResponse> requestAiReply(
            Role role,
            String message,
            List<AiAgentRuntimeRequest.HistoryMessage> history,
            List<AiAgentRuntimeRequest.Attachment> attachments,
            Map<String, Object> businessContext
    ) {
        try {
            AiAgentRuntimeRequest runtimeRequest = buildRuntimeRequest(role, message, history, attachments, businessContext);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/agents/respond"))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(runtimeRequest)))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI agent service returned status {}", response.statusCode());
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(response.body(), AiAgentRuntimeResponse.class));
        } catch (Exception ex) {
            log.warn("AI agent service failed", ex);
            return Optional.empty();
        }
    }

    private AiAgentRuntimeRequest buildRuntimeRequest(
            Role role,
            String message,
            List<AiAgentRuntimeRequest.HistoryMessage> history,
            List<AiAgentRuntimeRequest.Attachment> attachments,
            Map<String, Object> businessContext
    ) {
        return AiAgentRuntimeRequest.builder()
                .agent(defaultRuntimeAgent())
                .role(role == null ? Role.CUSTOMER : role)
                .message(message)
                .history(history == null ? List.of() : history)
                .attachments(attachments == null ? List.of() : attachments)
                .datasetIds(activeDatasetIds())
                .businessContext(businessContext == null ? buildBusinessContext(role) : businessContext)
                .build();
    }

    private AiAgentRuntimeRequest.RuntimeAgent defaultRuntimeAgent() {
        return AiAgentRuntimeRequest.RuntimeAgent.builder()
                .id(DEFAULT_CUSTOMER_AGENT_ID)
                .name(defaultAgentName)
                .systemPrompt(defaultAgentSystemPrompt)
                .guardrails(defaultAgentGuardrails)
                .temperature(BigDecimal.valueOf(0.3))
                .maxTokens(1000)
                .topK(5)
                .scoreThreshold(BigDecimal.valueOf(0.35))
                .fallbackMessage(defaultAgentFallbackMessage)
                .handoffEnabled(true)
                .handoffThreshold(BigDecimal.valueOf(0.50))
                .build();
    }

    private List<UUID> activeDatasetIds() {
        return aiDatasetRepository.findAll().stream()
                .filter(dataset -> dataset.getStatus() == AiDatasetStatus.ACTIVE)
                .map(AiDataset::getId)
                .toList();
    }

    private void ingestDocument(AiDocument document) {
        try {
            AiKnowledgeIngestRequest request = AiKnowledgeIngestRequest.builder()
                    .datasetId(document.getDataset().getId())
                    .documentId(document.getId())
                    .agentIds(List.of(DEFAULT_CUSTOMER_AGENT_ID))
                    .fileName(document.getFileName())
                    .contentType(document.getFileType())
                    .contentBase64(document.getContentBase64())
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/knowledge/documents/ingest"))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                document.setIngestStatus(AiDocumentStatus.FAILED);
                document.setErrorMessage("AI ingest failed with status " + response.statusCode());
                return;
            }

            AiKnowledgeIngestResponse ingestResponse = objectMapper.readValue(response.body(), AiKnowledgeIngestResponse.class);
            document.setChunkCount(ingestResponse.getChunkCount());
            document.setIngestStatus(AiDocumentStatus.READY);
            document.setErrorMessage(null);
        } catch (Exception ex) {
            document.setIngestStatus(AiDocumentStatus.FAILED);
            document.setErrorMessage(ex.getMessage());
            log.warn("Could not ingest AI document {}", document.getId(), ex);
        }
    }

    private void deleteDocumentFromAiService(UUID documentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/knowledge/documents/" + documentId))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .DELETE()
                    .build();
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.warn("Could not delete AI document {} from vector store", documentId, ex);
        }
    }

    @Transactional
    public void syncProductToAi(UUID productId) {
        Product product = productRepository.findProductDetailById(productId).orElse(null);
        if (product == null) {
            return;
        }

        List<Map<String, Object>> variantsPayload = new ArrayList<>();
        List<AiProductVectorStatus> syncingStatuses = new ArrayList<>();
        for (ProductVariant variant : product.getVariants()) {
            if (variant.isDeleted()) {
                continue;
            }

            AiProductVectorStatus status = ensureVectorStatus(product.getId(), variant.getId());
            status.setSyncStatus(AiProductVectorSyncStatus.SYNCING);
            status.setErrorMessage(null);
            syncingStatuses.add(status);

            variantsPayload.add(buildProductVariantPayload(product, variant));
        }

        if (variantsPayload.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = Map.of("variants", variantsPayload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/api/internal/products/sync"))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                markProductSyncFailed(syncingStatuses, "AI sync failed with status " + response.statusCode() + ": " + response.body());
                return;
            }

            Instant now = Instant.now();
            for (AiProductVectorStatus status : syncingStatuses) {
                status.setSyncStatus(AiProductVectorSyncStatus.SYNCED);
                status.setLastSyncedAt(now);
                status.setQdrantPresent(true);
                status.setErrorMessage(null);
            }
        } catch (Exception ex) {
            markProductSyncFailed(syncingStatuses, "Khong ket noi duoc ZenTech-AI tai " + normalizeBaseUrl(aiBaseUrl) + ": " + resolveExceptionMessage(ex));
            log.error("Could not sync product {} to AI service", productId, ex);
        }
    }

    @Transactional
    public void reindexProductsToAi() {
        try {
            HttpRequest reindexRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/api/internal/products/reindex"))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(reindexRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to clear product vectors collection, status: {}", response.statusCode());
                return;
            }

            for (Product product : productRepository.findAll()) {
                if (!product.isDeleted()) {
                    syncProductToAi(product.getId());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to reindex products to AI service", ex);
        }
    }

    private Map<String, Object> buildProductVariantPayload(Product product, ProductVariant variant) {
        StringBuilder searchTextBuilder = new StringBuilder();
        searchTextBuilder.append("Ten san pham: ").append(product.getProductName()).append("\n");
        if (variant.getName() != null && !variant.getName().isBlank()) {
            searchTextBuilder.append("Phien ban/Mau: ").append(variant.getName()).append("\n");
        }
        if (product.getSpecifications() != null) {
            searchTextBuilder.append("Thong so ky thuat: ").append(product.getSpecifications()).append("\n");
        }
        if (product.getCompatibility() != null) {
            searchTextBuilder.append("Compatibility: ").append(product.getCompatibility()).append("\n");
        }
        if (product.getBoxContents() != null) {
            searchTextBuilder.append("Box contents: ").append(product.getBoxContents()).append("\n");
        }
        if (product.getSupportInfo() != null) {
            searchTextBuilder.append("Support info: ").append(product.getSupportInfo()).append("\n");
        }
        if (product.getProductGroup() != null) {
            searchTextBuilder.append("Nhom san pham: ").append(product.getProductGroup().getGroupName()).append("\n");
        }
        if (product.getCategories() != null && !product.getCategories().isEmpty()) {
            searchTextBuilder.append("Danh muc: ");
            product.getCategories().forEach(category -> searchTextBuilder.append(category.getCategoryName()).append(", "));
            searchTextBuilder.append("\n");
        }

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("productId", product.getId().toString());
        varMap.put("variantId", variant.getId().toString());
        varMap.put("sku", "");
        varMap.put("name", product.getProductName());
        varMap.put("searchText", searchTextBuilder.toString());
        varMap.put("categoryId", product.getCategories() != null && !product.getCategories().isEmpty() ? product.getCategories().iterator().next().getId().toString() : null);
        varMap.put("categoryName", product.getCategories() != null && !product.getCategories().isEmpty() ? product.getCategories().iterator().next().getCategoryName() : null);
        varMap.put("colors", variant.getNameColor() != null ? List.of(variant.getNameColor()) : List.of());
        varMap.put("sizes", List.of());
        varMap.put("tags", List.of());
        varMap.put("imageKeys", product.getImageKeys() != null ? product.getImageKeys() : List.of());
        varMap.put("status", product.isDeleted() ? "INACTIVE" : "ACTIVE");
        varMap.put("updatedAt", Instant.now().toString());
        return varMap;
    }

    private void verifyStatuses(List<AiProductVectorStatus> statuses) {
        if (statuses.isEmpty()) {
            return;
        }

        try {
            AiProductVectorVerifyRequest request = AiProductVectorVerifyRequest.builder()
                    .items(statuses.stream()
                            .map(status -> AiProductVectorVerifyRequest.Item.builder()
                                    .productId(status.getProductId())
                                    .variantId(status.getVariantId())
                                    .build())
                            .toList())
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiBaseUrl) + "/api/internal/products/verify"))
                    .timeout(Duration.ofMillis(aiTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(aiTimeoutMs))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "AI verify failed with status " + response.statusCode();
                statuses.forEach(status -> status.setErrorMessage(message));
                return;
            }

            Map<UUID, AiProductVectorVerifyResponse.Item> resultByVariantId = objectMapper
                    .readValue(response.body(), AiProductVectorVerifyResponse.class)
                    .getItems()
                    .stream()
                    .collect(Collectors.toMap(AiProductVectorVerifyResponse.Item::getVariantId, Function.identity()));
            Instant now = Instant.now();
            for (AiProductVectorStatus status : statuses) {
                AiProductVectorVerifyResponse.Item item = resultByVariantId.get(status.getVariantId());
                if (item == null) {
                    continue;
                }
                status.setQdrantPresent(item.isPresent());
                status.setLastVerifiedAt(now);
                status.setErrorMessage(item.isPresent() ? null : "Khong tim thay vector trong Qdrant");
            }
        } catch (Exception ex) {
            statuses.forEach(status -> status.setErrorMessage(ex.getMessage()));
            log.warn("Could not verify product vectors", ex);
        }
    }

    private AiProductVectorStatus ensureVectorStatus(UUID productId, UUID variantId) {
        return aiProductVectorStatusRepository.findByVariantId(variantId)
                .orElseGet(() -> aiProductVectorStatusRepository.save(AiProductVectorStatus.builder()
                        .productId(productId)
                        .variantId(variantId)
                        .syncStatus(AiProductVectorSyncStatus.NOT_SYNCED)
                        .build()));
    }

    private ProductVariant findProductVariant(UUID variantId) {
        return productRepository.findAll().stream()
                .flatMap(product -> product.getVariants().stream())
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product variant", "id", variantId));
    }

    private void markProductSyncFailed(List<AiProductVectorStatus> statuses, String message) {
        String normalizedMessage = normalizeText(message);
        if (normalizedMessage == null) {
            normalizedMessage = "Dong bo san pham qua AI/Qdrant that bai.";
        }
        for (AiProductVectorStatus status : statuses) {
            status.setSyncStatus(AiProductVectorSyncStatus.FAILED);
            status.setErrorMessage(normalizedMessage);
        }
    }

    private String resolveExceptionMessage(Exception ex) {
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return ex.getClass().getSimpleName();
    }

    private boolean matchesProductVectorFilter(AiProductVectorStatusResponse response, String filter) {
        if (filter == null || filter.equalsIgnoreCase("ALL")) {
            return true;
        }
        return switch (filter.toUpperCase()) {
            case "SYNCED" -> response.getSyncStatus() == AiProductVectorSyncStatus.SYNCED && Boolean.TRUE.equals(response.getQdrantPresent());
            case "NOT_SYNCED" -> response.getSyncStatus() == AiProductVectorSyncStatus.NOT_SYNCED;
            case "FAILED" -> response.getSyncStatus() == AiProductVectorSyncStatus.FAILED;
            case "DRIFT" -> response.getSyncStatus() == AiProductVectorSyncStatus.SYNCED && Boolean.FALSE.equals(response.getQdrantPresent());
            default -> true;
        };
    }

    private Map<String, Object> buildBusinessContext(Role role) {
        CustomUserDetails user = SecurityContextUtils.getCurrentUser();
        return Map.of(
                "role", role == null ? Role.CUSTOMER.name() : role.name(),
                "userId", user == null ? "" : user.getId().toString(),
                "generatedAt", Instant.now().toString()
        );
    }

    private AiDataset getDatasetEntity(UUID datasetId) {
        return aiDatasetRepository.findDetailById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Dataset", "id", datasetId));
    }

    private AiDocument getDocumentEntity(UUID documentId) {
        return aiDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Document", "id", documentId));
    }

    private AiDatasetResponse toDatasetResponse(AiDataset dataset) {
        List<AiDocumentResponse> documents = dataset.getDocuments() == null
                ? List.of()
                : dataset.getDocuments().stream().map(this::toDocumentResponse).toList();
        return AiDatasetResponse.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .status(dataset.getStatus())
                .createdBy(dataset.getCreatedBy())
                .documents(documents)
                .documentCount(documents.size())
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .build();
    }

    private AiDocumentResponse toDocumentResponse(AiDocument document) {
        UUID datasetId = document.getDataset() == null ? null : document.getDataset().getId();
        return AiDocumentResponse.builder()
                .id(document.getId())
                .datasetId(datasetId)
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .ingestStatus(document.getIngestStatus())
                .chunkCount(document.getChunkCount())
                .errorMessage(document.getErrorMessage())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private AiProductVectorStatusResponse toProductVectorStatusResponse(
            Product product,
            ProductVariant variant,
            AiProductVectorStatus status
    ) {
        return AiProductVectorStatusResponse.builder()
                .productId(product.getId())
                .variantId(variant.getId())
                .productName(product.getProductName())
                .variantName(variant.getName())
                .imageKey(resolveProductImage(product))
                .syncStatus(status == null ? AiProductVectorSyncStatus.NOT_SYNCED : status.getSyncStatus())
                .lastSyncedAt(status == null ? null : status.getLastSyncedAt())
                .lastVerifiedAt(status == null ? null : status.getLastVerifiedAt())
                .qdrantPresent(status == null ? null : status.getQdrantPresent())
                .errorMessage(status == null ? null : status.getErrorMessage())
                .build();
    }

    private String resolveProductImage(Product product) {
        if (product.getRepresentativeImageKey() != null && !product.getRepresentativeImageKey().isBlank()) {
            return product.getRepresentativeImageKey();
        }
        if (product.getImageKeys() != null && !product.getImageKeys().isEmpty()) {
            return product.getImageKeys().get(0);
        }
        return null;
    }

    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Document file is required");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new RuntimeException("AI document must be 10MB or smaller");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean supportedExtension = fileName.endsWith(".pdf") || fileName.endsWith(".txt") || fileName.endsWith(".md");
        if (!supportedExtension && !SUPPORTED_TYPES.contains(resolveContentType(file))) {
            throw new RuntimeException("Only PDF, TXT, and MD documents are supported");
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
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
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

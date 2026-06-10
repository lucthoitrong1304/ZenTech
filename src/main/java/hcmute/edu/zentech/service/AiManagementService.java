package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.AiAgentDatasetsRequest;
import hcmute.edu.zentech.dto.request.AiAgentDemoRequest;
import hcmute.edu.zentech.dto.request.AiAgentRequest;
import hcmute.edu.zentech.dto.request.AiAgentRolesRequest;
import hcmute.edu.zentech.dto.request.AiAgentRuntimeRequest;
import hcmute.edu.zentech.dto.request.AiDatasetRequest;
import hcmute.edu.zentech.dto.request.AiKnowledgeIngestRequest;
import hcmute.edu.zentech.dto.response.AiAgentDemoResponse;
import hcmute.edu.zentech.dto.response.AiAgentResponse;
import hcmute.edu.zentech.dto.response.AiAgentRuntimeResponse;
import hcmute.edu.zentech.dto.response.AiDatasetResponse;
import hcmute.edu.zentech.dto.response.AiDocumentResponse;
import hcmute.edu.zentech.dto.response.AiKnowledgeIngestResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.AiAgent;
import hcmute.edu.zentech.model.AiAgentStatus;
import hcmute.edu.zentech.model.AiDataset;
import hcmute.edu.zentech.model.AiDatasetStatus;
import hcmute.edu.zentech.model.AiDocument;
import hcmute.edu.zentech.model.AiDocumentStatus;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AiAgentRepository;
import hcmute.edu.zentech.repository.AiDatasetRepository;
import hcmute.edu.zentech.repository.AiDocumentRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AiManagementService {
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "application/octet-stream"
    );

    private final AiAgentRepository aiAgentRepository;
    private final AiDatasetRepository aiDatasetRepository;
    private final AiDocumentRepository aiDocumentRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${app.ai.timeout-ms:15000}")
    private long aiTimeoutMs;

    public List<AiAgentResponse> getAgents() {
        return aiAgentRepository.findAllActiveRecords().stream()
                .map(this::toAgentResponse)
                .toList();
    }

    public AiAgentResponse getAgent(UUID agentId) {
        return toAgentResponse(getAgentEntity(agentId));
    }

    @Transactional
    public AiAgentResponse createAgent(AiAgentRequest request) {
        AiAgent agent = AiAgent.builder().build();
        applyAgentRequest(agent, request);
        validateDefaultRoles(agent, null);
        return toAgentResponse(aiAgentRepository.save(agent));
    }

    @Transactional
    public AiAgentResponse updateAgent(UUID agentId, AiAgentRequest request) {
        AiAgent agent = getAgentEntity(agentId);
        applyAgentRequest(agent, request);
        validateDefaultRoles(agent, agentId);
        return toAgentResponse(agent);
    }

    @Transactional
    public AiAgentResponse deleteAgent(UUID agentId) {
        AiAgent agent = getAgentEntity(agentId);
        agent.setDeleted(true);
        agent.setDeletedAt(Instant.now());
        agent.setStatus(AiAgentStatus.INACTIVE);
        return toAgentResponse(agent);
    }

    @Transactional
    public AiAgentResponse updateAgentRoles(UUID agentId, AiAgentRolesRequest request) {
        AiAgent agent = getAgentEntity(agentId);
        agent.setAssignedRoles(new HashSet<>(request.getAssignedRoles()));
        agent.setDefaultForRole(request.isDefaultForRole());
        agent.setPriority(request.getPriority());
        validateDefaultRoles(agent, agentId);
        return toAgentResponse(agent);
    }

    @Transactional
    public AiAgentResponse updateAgentDatasets(UUID agentId, AiAgentDatasetsRequest request) {
        AiAgent agent = getAgentEntity(agentId);
        agent.setDatasets(new HashSet<>(aiDatasetRepository.findAllById(request.getDatasetIds())));
        return toAgentResponse(agent);
    }

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

    public Optional<AiAgent> findAgentForRole(Role role) {
        return aiAgentRepository.findRuntimeCandidates(role, AiAgentStatus.ACTIVE).stream().findFirst();
    }

    public Optional<String> generateRuntimeReply(
            Role role,
            String message,
            List<AiAgentRuntimeRequest.HistoryMessage> history,
            List<AiAgentRuntimeRequest.Attachment> attachments,
            Map<String, Object> businessContext
    ) {
        return findAgentForRole(role)
                .flatMap(agent -> requestAiReply(agent, role, message, history, attachments, businessContext)
                        .map(AiAgentRuntimeResponse::getContent)
                        .map(this::normalizeText));
    }

    public AiAgentDemoResponse demoAgent(UUID agentId, AiAgentDemoRequest request) {
        AiAgent agent = getAgentEntity(agentId);
        Role role = resolvePrimaryRole(agent);
        AiAgentRuntimeResponse response = requestAiReply(
                agent,
                role,
                request.getMessage(),
                request.getHistory() == null ? List.of() : request.getHistory().stream()
                        .map(h -> AiAgentRuntimeRequest.HistoryMessage.builder()
                                .role(h.getRole())
                                .content(h.getContent())
                                .build())
                        .toList(),
                List.of(), // no attachments for demo
                buildBusinessContext(role)
        ).orElseGet(() -> {
            AiAgentRuntimeResponse fallback = new AiAgentRuntimeResponse();
            fallback.setContent(resolveFallback(agent));
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

    private void applyAgentRequest(AiAgent agent, AiAgentRequest request) {
        agent.setName(requireText(request.getName(), "Agent name is required"));
        agent.setDescription(normalizeText(request.getDescription()));
        agent.setStatus(request.getStatus() == null ? AiAgentStatus.INACTIVE : request.getStatus());
        agent.setAssignedRoles(new HashSet<>(request.getAssignedRoles()));
        agent.setPriority(request.getPriority());
        agent.setDefaultForRole(request.isDefaultForRole());
        agent.setSystemPrompt(requireText(request.getSystemPrompt(), "System prompt is required"));
        agent.setGuardrails(normalizeText(request.getGuardrails()));
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setTopK(request.getTopK());
        agent.setScoreThreshold(request.getScoreThreshold());
        agent.setFallbackMessage(normalizeText(request.getFallbackMessage()));
        agent.setHandoffEnabled(request.isHandoffEnabled());
        agent.setHandoffThreshold(request.getHandoffThreshold());
        agent.setDatasets(new HashSet<>(aiDatasetRepository.findAllById(request.getDatasetIds())));
    }

    private void validateDefaultRoles(AiAgent agent, UUID agentId) {
        if (agent.getStatus() != AiAgentStatus.ACTIVE || !agent.isDefaultForRole()) {
            return;
        }

        for (Role role : agent.getAssignedRoles()) {
            if (aiAgentRepository.existsOtherActiveDefaultForRole(role, agentId, AiAgentStatus.ACTIVE)) {
                throw new RuntimeException("Only one active default AI agent is allowed for role " + role);
            }
        }
    }

    private Optional<AiAgentRuntimeResponse> requestAiReply(
            AiAgent agent,
            Role role,
            String message,
            List<AiAgentRuntimeRequest.HistoryMessage> history,
            List<AiAgentRuntimeRequest.Attachment> attachments,
            Map<String, Object> businessContext
    ) {
        try {
            AiAgentRuntimeRequest runtimeRequest = AiAgentRuntimeRequest.builder()
                    .agent(toRuntimeAgent(agent))
                    .role(role)
                    .message(message)
                    .history(history == null ? List.of() : history)
                    .attachments(attachments == null ? List.of() : attachments)
                    .datasetIds(agent.getDatasets().stream().map(AiDataset::getId).toList())
                    .businessContext(businessContext == null ? Map.of() : businessContext)
                    .build();

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
                log.warn("AI agent service returned status {} for agent {}", response.statusCode(), agent.getId());
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(response.body(), AiAgentRuntimeResponse.class));
        } catch (Exception ex) {
            log.warn("AI agent service failed for agent {}", agent.getId(), ex);
            return Optional.empty();
        }
    }

    private void ingestDocument(AiDocument document) {
        try {
            AiKnowledgeIngestRequest request = AiKnowledgeIngestRequest.builder()
                    .datasetId(document.getDataset().getId())
                    .documentId(document.getId())
                    .agentIds(resolveAgentIdsForDataset(document.getDataset().getId()))
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

    private List<UUID> resolveAgentIdsForDataset(UUID datasetId) {
        List<UUID> agentIds = new ArrayList<>();
        for (AiAgent agent : aiAgentRepository.findAllActiveRecords()) {
            boolean attached = agent.getDatasets().stream().anyMatch(dataset -> dataset.getId().equals(datasetId));
            if (attached) {
                agentIds.add(agent.getId());
            }
        }
        return agentIds;
    }

    private AiAgentRuntimeRequest.RuntimeAgent toRuntimeAgent(AiAgent agent) {
        return AiAgentRuntimeRequest.RuntimeAgent.builder()
                .id(agent.getId())
                .name(agent.getName())
                .systemPrompt(agent.getSystemPrompt())
                .guardrails(agent.getGuardrails())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topK(agent.getTopK())
                .scoreThreshold(agent.getScoreThreshold())
                .fallbackMessage(resolveFallback(agent))
                .handoffEnabled(agent.isHandoffEnabled())
                .handoffThreshold(agent.getHandoffThreshold())
                .build();
    }

    private Map<String, Object> buildBusinessContext(Role role) {
        CustomUserDetails user = SecurityContextUtils.getCurrentUser();
        return Map.of(
                "role", role.name(),
                "userId", user == null ? "" : user.getId().toString(),
                "generatedAt", Instant.now().toString()
        );
    }

    private Role resolvePrimaryRole(AiAgent agent) {
        return agent.getAssignedRoles().stream()
                .min(Comparator.comparingInt(Role::ordinal))
                .orElse(Role.CUSTOMER);
    }

    private AiAgent getAgentEntity(UUID agentId) {
        return aiAgentRepository.findDetailById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Agent", "id", agentId));
    }

    private AiDataset getDatasetEntity(UUID datasetId) {
        return aiDatasetRepository.findDetailById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Dataset", "id", datasetId));
    }

    private AiDocument getDocumentEntity(UUID documentId) {
        return aiDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Document", "id", documentId));
    }

    private AiAgentResponse toAgentResponse(AiAgent agent) {
        List<AiDatasetResponse> datasets = agent.getDatasets().stream()
                .map(this::toDatasetResponse)
                .toList();
        return AiAgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .status(agent.getStatus())
                .assignedRoles(agent.getAssignedRoles())
                .priority(agent.getPriority())
                .defaultForRole(agent.isDefaultForRole())
                .systemPrompt(agent.getSystemPrompt())
                .guardrails(agent.getGuardrails())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topK(agent.getTopK())
                .scoreThreshold(agent.getScoreThreshold())
                .fallbackMessage(agent.getFallbackMessage())
                .handoffEnabled(agent.isHandoffEnabled())
                .handoffThreshold(agent.getHandoffThreshold())
                .datasets(datasets)
                .datasetCount(datasets.size())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
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

    private String resolveFallback(AiAgent agent) {
        String fallback = normalizeText(agent.getFallbackMessage());
        return fallback == null
                ? "AI chua co du du lieu de tra loi chac chan. Vui long bo sung dataset hoac lien he nguoi phu trach."
                : fallback;
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

package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.AiAgentDatasetsRequest;
import hcmute.edu.zentech.dto.request.AiAgentDemoRequest;
import hcmute.edu.zentech.dto.request.AiAgentRequest;
import hcmute.edu.zentech.dto.request.AiAgentRolesRequest;
import hcmute.edu.zentech.dto.request.AiDatasetRequest;
import hcmute.edu.zentech.dto.response.AiAgentDemoResponse;
import hcmute.edu.zentech.dto.response.AiAgentResponse;
import hcmute.edu.zentech.dto.response.AiDatasetResponse;
import hcmute.edu.zentech.dto.response.AiDocumentResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.service.AiManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/ai")
@RequiredArgsConstructor
public class AiManagementController {
    private final AiManagementService aiManagementService;

    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<List<AiAgentResponse>>> getAgents() {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getAgents()));
    }

    @PostMapping("/agents")
    @TrackActivity(action = ActivityAction.CREATE_AI_AGENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_AGENT", severity = ActivitySeverity.INFO, summary = "Tạo AI agent")
    public ResponseEntity<ApiResponse<AiAgentResponse>> createAgent(@Valid @RequestBody AiAgentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.createAgent(request)));
    }

    @GetMapping("/agents/{agentId}")
    public ResponseEntity<ApiResponse<AiAgentResponse>> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getAgent(agentId)));
    }

    @PatchMapping("/agents/{agentId}")
    @TrackActivity(action = ActivityAction.UPDATE_AI_AGENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_AGENT", severity = ActivitySeverity.INFO, summary = "Cập nhật AI agent")
    public ResponseEntity<ApiResponse<AiAgentResponse>> updateAgent(
            @PathVariable UUID agentId,
            @Valid @RequestBody AiAgentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.updateAgent(agentId, request)));
    }

    @DeleteMapping("/agents/{agentId}")
    @TrackActivity(action = ActivityAction.DELETE_AI_AGENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_AGENT", severity = ActivitySeverity.IMPORTANT, summary = "Xóa AI agent")
    public ResponseEntity<ApiResponse<AiAgentResponse>> deleteAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.deleteAgent(agentId)));
    }

    @PatchMapping("/agents/{agentId}/roles")
    @TrackActivity(action = ActivityAction.CHANGE_AI_AGENT_ROLE, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_AGENT", severity = ActivitySeverity.INFO, summary = "Cập nhật vai trò phục vụ của AI agent")
    public ResponseEntity<ApiResponse<AiAgentResponse>> updateAgentRoles(
            @PathVariable UUID agentId,
            @Valid @RequestBody AiAgentRolesRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.updateAgentRoles(agentId, request)));
    }

    @PatchMapping("/agents/{agentId}/datasets")
    @TrackActivity(action = ActivityAction.UPDATE_AI_AGENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_AGENT", severity = ActivitySeverity.INFO, summary = "Cập nhật bộ dữ liệu liên kết với AI agent")
    public ResponseEntity<ApiResponse<AiAgentResponse>> updateAgentDatasets(
            @PathVariable UUID agentId,
            @RequestBody AiAgentDatasetsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.updateAgentDatasets(agentId, request)));
    }

    @PostMapping("/agents/{agentId}/demo")
    public ResponseEntity<ApiResponse<AiAgentDemoResponse>> demoAgent(
            @PathVariable UUID agentId,
            @Valid @RequestBody AiAgentDemoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.demoAgent(agentId, request)));
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<AiDatasetResponse>>> getDatasets() {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getDatasets()));
    }

    @PostMapping("/datasets")
    @TrackActivity(action = ActivityAction.CREATE_AI_DATASET, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DATASET", severity = ActivitySeverity.INFO, summary = "Tạo bộ dữ liệu AI")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> createDataset(@Valid @RequestBody AiDatasetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.createDataset(request)));
    }

    @GetMapping("/datasets/{datasetId}")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> getDataset(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getDataset(datasetId)));
    }

    @PatchMapping("/datasets/{datasetId}")
    @TrackActivity(action = ActivityAction.UPDATE_AI_DATASET, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DATASET", severity = ActivitySeverity.INFO, summary = "Cập nhật bộ dữ liệu AI")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> updateDataset(
            @PathVariable UUID datasetId,
            @Valid @RequestBody AiDatasetRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.updateDataset(datasetId, request)));
    }

    @DeleteMapping("/datasets/{datasetId}")
    @TrackActivity(action = ActivityAction.DELETE_AI_DATASET, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DATASET", severity = ActivitySeverity.IMPORTANT, summary = "Xóa bộ dữ liệu AI")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> deleteDataset(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.deleteDataset(datasetId)));
    }

    @PostMapping(
            value = "/datasets/{datasetId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @TrackActivity(action = ActivityAction.UPLOAD_AI_DOCUMENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DOCUMENT", severity = ActivitySeverity.INFO, summary = "Tải lên tài liệu AI")
    public ResponseEntity<ApiResponse<AiDocumentResponse>> uploadDocument(
            @PathVariable UUID datasetId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.uploadDocument(datasetId, file)));
    }

    @DeleteMapping("/documents/{documentId}")
    @TrackActivity(action = ActivityAction.DELETE_AI_DOCUMENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DOCUMENT", severity = ActivitySeverity.IMPORTANT, summary = "Xóa tài liệu AI")
    public ResponseEntity<ApiResponse<AiDocumentResponse>> deleteDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.deleteDocument(documentId)));
    }

    @PostMapping("/documents/{documentId}/reingest")
    public ResponseEntity<ApiResponse<AiDocumentResponse>> reingestDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.reingestDocument(documentId)));
    }

    @PostMapping("/products/reindex")
    public ResponseEntity<ApiResponse<String>> reindexProducts() {
        aiManagementService.reindexProductsToAi();
        return ResponseEntity.ok(ApiResponse.success("Reindex started"));
    }
}

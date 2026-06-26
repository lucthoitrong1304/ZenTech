package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.dto.request.AiAgentDemoRequest;
import hcmute.edu.zentech.dto.request.AiDatasetRequest;
import hcmute.edu.zentech.dto.response.AiAgentDemoResponse;
import hcmute.edu.zentech.dto.response.AiDatasetResponse;
import hcmute.edu.zentech.dto.response.AiDocumentResponse;
import hcmute.edu.zentech.dto.response.AiProductVectorStatusResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.model.AiProductVectorSyncStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/demo")
    public ResponseEntity<ApiResponse<AiAgentDemoResponse>> demoAgent(@Valid @RequestBody AiAgentDemoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.demoAgent(request)));
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<AiDatasetResponse>>> getDatasets() {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getDatasets()));
    }

    @PostMapping("/datasets")
    @TrackActivity(action = ActivityAction.CREATE_AI_DATASET, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DATASET", severity = ActivitySeverity.INFO, summary = "Tao bo du lieu AI")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> createDataset(@Valid @RequestBody AiDatasetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.createDataset(request)));
    }

    @GetMapping("/datasets/{datasetId}")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> getDataset(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getDataset(datasetId)));
    }

    @PatchMapping("/datasets/{datasetId}")
    @TrackActivity(action = ActivityAction.UPDATE_AI_DATASET, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DATASET", severity = ActivitySeverity.INFO, summary = "Cap nhat bo du lieu AI")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> updateDataset(
            @PathVariable UUID datasetId,
            @Valid @RequestBody AiDatasetRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.updateDataset(datasetId, request)));
    }

    @DeleteMapping("/datasets/{datasetId}")
    @TrackActivity(action = ActivityAction.DELETE_AI_DATASET, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DATASET", severity = ActivitySeverity.IMPORTANT, summary = "Archive bo du lieu AI")
    public ResponseEntity<ApiResponse<AiDatasetResponse>> deleteDataset(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.deleteDataset(datasetId)));
    }

    @PostMapping(
            value = "/datasets/{datasetId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @TrackActivity(action = ActivityAction.UPLOAD_AI_DOCUMENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DOCUMENT", severity = ActivitySeverity.INFO, summary = "Tai len tai lieu AI")
    public ResponseEntity<ApiResponse<AiDocumentResponse>> uploadDocument(
            @PathVariable UUID datasetId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.uploadDocument(datasetId, file)));
    }

    @DeleteMapping("/documents/{documentId}")
    @TrackActivity(action = ActivityAction.DELETE_AI_DOCUMENT, area = ActivityArea.MANAGEMENT, module = "AI", targetType = "AI_DOCUMENT", severity = ActivitySeverity.IMPORTANT, summary = "Xoa tai lieu AI")
    public ResponseEntity<ApiResponse<AiDocumentResponse>> deleteDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.deleteDocument(documentId)));
    }

    @PostMapping("/documents/{documentId}/reingest")
    public ResponseEntity<ApiResponse<AiDocumentResponse>> reingestDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.reingestDocument(documentId)));
    }

    @GetMapping("/products/vector-status")
    public ResponseEntity<ApiResponse<List<AiProductVectorStatusResponse>>> getProductVectorStatuses(
            @RequestParam(required = false) String filter
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.getProductVectorStatuses(filter)));
    }

    @PostMapping("/products/variants/{variantId}/sync")
    public ResponseEntity<ApiResponse<AiProductVectorStatusResponse>> syncProductVariant(@PathVariable UUID variantId) {
        AiProductVectorStatusResponse response = aiManagementService.syncProductVariantToAi(variantId);
        if (response.getSyncStatus() == AiProductVectorSyncStatus.FAILED) {
            throw new RuntimeException(response.getErrorMessage() == null
                    ? "Dong bo san pham qua AI/Qdrant that bai."
                    : response.getErrorMessage());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/products/variants/{variantId}/verify")
    public ResponseEntity<ApiResponse<AiProductVectorStatusResponse>> verifyProductVariant(@PathVariable UUID variantId) {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.verifyProductVariantInQdrant(variantId)));
    }

    @PostMapping("/products/verify")
    public ResponseEntity<ApiResponse<List<AiProductVectorStatusResponse>>> verifyAllProducts() {
        return ResponseEntity.ok(ApiResponse.success(aiManagementService.verifyAllProductVectors()));
    }

    @PostMapping("/products/reindex")
    public ResponseEntity<ApiResponse<String>> reindexProducts() {
        aiManagementService.reindexProductsToAi();
        return ResponseEntity.ok(ApiResponse.success("Reindex started"));
    }
}

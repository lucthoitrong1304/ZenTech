package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.ProductGroupCreateRequest;
import hcmute.edu.zentech.dto.request.ProductGroupUpdateRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.ProductGroupResponse;
import hcmute.edu.zentech.service.ProductGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/product-groups")
@RequiredArgsConstructor
public class ProductGroupManagementController {
    private final ProductGroupService productGroupService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductGroupResponse>>> getGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "groupName,asc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                productGroupService.getGroups(page, size, sort, keyword, includeDeleted)
        ));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<ProductGroupResponse>> getGroupDetail(@PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.success(productGroupService.getGroupDetail(groupId)));
    }

    @PostMapping
    @TrackActivity(action = ActivityAction.CREATE_PRODUCT_GROUP, area = ActivityArea.MANAGEMENT, module = "PRODUCT", targetType = "PRODUCT_GROUP", severity = ActivitySeverity.IMPORTANT, summary = "Tạo nhóm sản phẩm")
    public ResponseEntity<ApiResponse<ProductGroupResponse>> createGroup(
            @Valid @RequestBody ProductGroupCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productGroupService.createGroup(request)));
    }

    @PatchMapping("/{groupId}")
    @TrackActivity(action = ActivityAction.UPDATE_PRODUCT_GROUP, area = ActivityArea.MANAGEMENT, module = "PRODUCT", targetType = "PRODUCT_GROUP", severity = ActivitySeverity.IMPORTANT, summary = "Cập nhật nhóm sản phẩm")
    public ResponseEntity<ApiResponse<ProductGroupResponse>> updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody ProductGroupUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(productGroupService.updateGroup(groupId, request)));
    }

    @DeleteMapping("/{groupId}")
    @TrackActivity(action = ActivityAction.DELETE_PRODUCT_GROUP, area = ActivityArea.MANAGEMENT, module = "PRODUCT", targetType = "PRODUCT_GROUP", severity = ActivitySeverity.CRITICAL, summary = "Xóa nhóm sản phẩm")
    public ResponseEntity<ApiResponse<ProductGroupResponse>> deleteGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.success(productGroupService.deleteGroup(groupId)));
    }
}

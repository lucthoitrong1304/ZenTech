package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.UpdateRolePermissionsRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PermissionMatrixResponse;
import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> getPermissionMatrix() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getMatrix()));
    }

    @PutMapping("/{role}")
    public ResponseEntity<ApiResponse<Set<PermissionCode>>> updateRolePermissions(
            @PathVariable Role role,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                permissionService.replaceRolePermissions(role, request.getPermissions())
        ));
    }
}

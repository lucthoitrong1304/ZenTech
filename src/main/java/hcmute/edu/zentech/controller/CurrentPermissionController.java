package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CurrentPermissionsResponse;
import hcmute.edu.zentech.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/me/permissions")
@RequiredArgsConstructor
public class CurrentPermissionController {
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<CurrentPermissionsResponse>> getCurrentPermissions() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getCurrentPermissions()));
    }
}

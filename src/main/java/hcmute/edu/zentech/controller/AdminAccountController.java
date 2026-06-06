package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.CreateInternalAccountRequest;
import hcmute.edu.zentech.dto.request.UpdateAccountRoleRequest;
import hcmute.edu.zentech.dto.request.UpdateAccountStatusRequest;
import hcmute.edu.zentech.dto.response.AccountSummaryResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AccountSummaryResponse>>> getAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAccountService.getAccounts(page, size, sort, keyword, role, active)
        ));
    }

    @PatchMapping("/{accountId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateAccountRole(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRoleRequest request
    ) {
        adminAccountService.updateAccountRole(accountId, request.getRole());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Cập nhật quyền thành công").build());
    }

    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateAccountStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request
    ) {
        adminAccountService.updateAccountStatus(accountId, request.getActive());
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Cập nhật trạng thái thành công").build());
    }

    @PostMapping("/internal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> createInternalAccount(
            @Valid @RequestBody CreateInternalAccountRequest request
    ) {
        adminAccountService.createInternalAccount(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Tạo tài khoản nội bộ thành công").build());
    }
}

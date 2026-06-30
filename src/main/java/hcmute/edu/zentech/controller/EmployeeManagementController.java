package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.EmployeeCreateRequest;
import hcmute.edu.zentech.dto.request.EmployeeLeaveQuotaUpdateRequest;
import hcmute.edu.zentech.dto.request.EmployeeUpdateRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.EmployeeLeaveQuotaResponse;
import hcmute.edu.zentech.dto.response.EmployeeSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.service.EmployeeManagementService;
import hcmute.edu.zentech.service.LeaveManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/employees")
@RequiredArgsConstructor
public class EmployeeManagementController {
    private final EmployeeManagementService employeeManagementService;
    private final LeaveManagementService leaveManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EmployeeSummaryResponse>>> getEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Role role
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                employeeManagementService.getEmployees(page, size, sort, keyword, active, role)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeSummaryResponse>> createEmployee(
            @Valid @RequestBody EmployeeCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(employeeManagementService.createEmployee(request)));
    }

    @PatchMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<EmployeeSummaryResponse>> updateEmployee(
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(employeeManagementService.updateEmployee(employeeId, request)));
    }

    @GetMapping("/{employeeId}/leave-quotas")
    public ResponseEntity<ApiResponse<List<EmployeeLeaveQuotaResponse>>> getLeaveQuotas(
            @PathVariable UUID employeeId,
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success(leaveManagementService.getEmployeeQuotas(employeeId, targetYear)));
    }

    @PatchMapping("/{employeeId}/leave-quotas")
    public ResponseEntity<ApiResponse<List<EmployeeLeaveQuotaResponse>>> updateLeaveQuotas(
            @PathVariable UUID employeeId,
            @RequestParam(required = false) Integer year,
            @Valid @RequestBody EmployeeLeaveQuotaUpdateRequest request
    ) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success(leaveManagementService.updateEmployeeQuotas(employeeId, targetYear, request)));
    }
}

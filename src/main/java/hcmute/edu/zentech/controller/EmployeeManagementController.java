package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.EmployeeCreateRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.EmployeeSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.service.EmployeeManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/employees")
@RequiredArgsConstructor
public class EmployeeManagementController {
    private final EmployeeManagementService employeeManagementService;

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
}

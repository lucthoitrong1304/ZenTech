package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.EmployeeProfileUpdateRequest;
import hcmute.edu.zentech.dto.request.FaceRegistrationRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.EmployeeProfileResponse;
import hcmute.edu.zentech.service.EmployeeSelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/me")
@RequiredArgsConstructor
public class EmployeeSelfController {
    private final EmployeeSelfService employeeSelfService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<EmployeeProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(employeeSelfService.getMyProfile()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<EmployeeProfileResponse>> updateMyProfile(
            @Valid @RequestBody EmployeeProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(employeeSelfService.updateMyProfile(request)));
    }

    @PostMapping("/face")
    public ResponseEntity<ApiResponse<Void>> registerFace(
            @Valid @RequestBody FaceRegistrationRequest request
    ) {
        employeeSelfService.registerFace(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Đăng ký khuôn mặt thành công.")
                .build());
    }

    @DeleteMapping("/face")
    public ResponseEntity<ApiResponse<Void>> deleteFace() {
        employeeSelfService.deleteFace();
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Xóa khuôn mặt thành công.")
                .build());
    }
}

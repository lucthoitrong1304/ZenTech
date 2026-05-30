package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.EmployeeProfileResponse;
import hcmute.edu.zentech.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<EmployeeProfileResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request
    ) {
        EmployeeProfileResponse employee = attendanceService.checkIn(request);
        return ResponseEntity.ok(ApiResponse.<EmployeeProfileResponse>builder()
                .success(true)
                .data(employee)
                .message("Check-in thành công. Xin chào " + employee.getFullName())
                .build());
    }
}

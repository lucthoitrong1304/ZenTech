package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.attendance.AttendanceLocationPolicyDto;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.service.AttendanceLocationPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance/location-policy")
@RequiredArgsConstructor
public class AttendanceLocationPolicyController {
    private final AttendanceLocationPolicyService policyService;

    @GetMapping
    public ResponseEntity<ApiResponse<AttendanceLocationPolicyDto>> getPolicy() {
        return ResponseEntity.ok(ApiResponse.success(policyService.getPolicy()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AttendanceLocationPolicyDto>> updatePolicy(
            @RequestBody AttendanceLocationPolicyDto dto
    ) {
        return ResponseEntity.ok(ApiResponse.success(policyService.updatePolicy(dto)));
    }
}

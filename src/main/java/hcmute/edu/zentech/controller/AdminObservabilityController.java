package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.AdminDependencyDetailResponse;
import hcmute.edu.zentech.dto.response.AdminObservabilityResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.service.AdminObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/observability")
@RequiredArgsConstructor
public class AdminObservabilityController {
    private final AdminObservabilityService observabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminObservabilityResponse>> getObservability(
            @RequestParam(defaultValue = "7D") String period,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.success(observabilityService.getObservability(period, from, to)));
    }

    @GetMapping("/dependencies/{name}")
    public ResponseEntity<ApiResponse<AdminDependencyDetailResponse>> getDependencyDetail(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(observabilityService.getDependencyDetail(name)));
    }
}

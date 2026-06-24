package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.AdminDashboardResponse;
import hcmute.edu.zentech.dto.response.AdminResourceMetricsResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard(
            @RequestParam(defaultValue = "7D") String period,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboard(period, from, to)));
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<AdminResourceMetricsResponse>> getResources() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getResourceMetrics()));
    }
}

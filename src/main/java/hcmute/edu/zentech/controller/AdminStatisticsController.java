package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.AdminStatisticsResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {
    private final AdminStatisticsService statisticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminStatisticsResponse>> getStatistics(
            @RequestParam(defaultValue = "7D") String period,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.success(statisticsService.getStatistics(period, from, to)));
    }
}

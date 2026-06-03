package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.*;
import hcmute.edu.zentech.service.ReportManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/management/reports")
@RequiredArgsConstructor
public class ReportManagementController {

    private final ReportManagementService reportManagementService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReportManagementSummaryResponse>> getReportsSummary(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getReportsSummary(startDate, endDate)
        ));
    }

    @GetMapping("/revenue-series")
    public ResponseEntity<ApiResponse<List<ReportManagementRevenueSeriesResponse>>> getRevenueSeries(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getRevenueSeries(startDate, endDate)
        ));
    }

    @GetMapping("/product-performance")
    public ResponseEntity<ApiResponse<List<ReportManagementProductPerformanceResponse>>> getProductPerformance(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getProductPerformance(startDate, endDate)
        ));
    }

    @GetMapping("/coupon-performance")
    public ResponseEntity<ApiResponse<List<ReportManagementCouponPerformanceResponse>>> getCouponPerformance(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getCouponPerformance(startDate, endDate)
        ));
    }

    @GetMapping("/customer-segments")
    public ResponseEntity<ApiResponse<List<ReportManagementCustomerSegmentResponse>>> getCustomerSegments(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getCustomerSegments(startDate, endDate)
        ));
    }

    @GetMapping("/ai-insights")
    public ResponseEntity<ApiResponse<List<AIOpsInsightResponse>>> getAIOpsInsights(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getAIOpsInsights(startDate, endDate)
        ));
    }

    @GetMapping("/payment-methods")
    public ResponseEntity<ApiResponse<List<ReportManagementPaymentMethodShareResponse>>> getPaymentMethodShare(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getPaymentMethodShare(startDate, endDate)
        ));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ReportManagementCategoryShareResponse>>> getCategoryShare(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getCategoryShare(startDate, endDate)
        ));
    }

    @GetMapping("/inventory-stats")
    public ResponseEntity<ApiResponse<ReportManagementInventoryStatsResponse>> getInventoryStats(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.getInventoryStats(startDate, endDate)
        ));
    }

    @GetMapping("/analyze")
    public ResponseEntity<ApiResponse<ReportManagementAIAnalyzeResponse>> analyzeReport(
            @RequestParam String tab,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportManagementService.analyzeReport(tab, startDate, endDate)
        ));
    }
}

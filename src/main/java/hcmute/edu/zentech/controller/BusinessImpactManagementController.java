package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.AffectedUserDetailDto;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.ManagementImpactDashboardDto;
import hcmute.edu.zentech.dto.response.ManagementIncidentImpactDto;
import hcmute.edu.zentech.dto.response.PageResponse;
import java.util.List;
import hcmute.edu.zentech.service.BusinessImpactManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/impact-analysis")
@RequiredArgsConstructor
public class BusinessImpactManagementController {

    private final BusinessImpactManagementService businessImpactManagementService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<ManagementImpactDashboardDto>> getDashboardStats(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(businessImpactManagementService.getDashboardStats(startDate, endDate)));
    }

    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<PageResponse<ManagementIncidentImpactDto>>> getIncidents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Sắp xếp incident từ mới đến cũ theo ngày xảy ra lỗi
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt")
        ));
        Page<ManagementIncidentImpactDto> pageResult = businessImpactManagementService.getIncidentsWithImpact(
                search, startDate, endDate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(pageResult, pageResult.getContent())));
    }

    @GetMapping("/incidents/{incidentId}")
    public ResponseEntity<ApiResponse<ManagementIncidentImpactDto>> getIncidentImpactDetail(
            @PathVariable UUID incidentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(businessImpactManagementService.getIncidentImpactDetail(incidentId)));
    }

    @PostMapping("/incidents/{incidentId}/analyze-ai")
    public ResponseEntity<ApiResponse<ManagementIncidentImpactDto>> generateAiSummary(
            @PathVariable UUID incidentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(businessImpactManagementService.generateAiSummary(incidentId)));
    }

    @GetMapping("/incidents/{incidentId}/affected-users")
    public ResponseEntity<ApiResponse<List<AffectedUserDetailDto>>> getAffectedUsers(
            @PathVariable UUID incidentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(businessImpactManagementService.getAffectedUserDetails(incidentId)));
    }
}

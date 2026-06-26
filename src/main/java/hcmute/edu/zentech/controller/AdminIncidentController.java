package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.IncidentCreateRequest;
import hcmute.edu.zentech.dto.request.IncidentCreateFromIssueRequest;
import hcmute.edu.zentech.dto.request.IssueLinkLookupRequest;
import hcmute.edu.zentech.dto.request.IncidentUpdateRequest;
import hcmute.edu.zentech.dto.response.AiAnalysisResponseDto;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.dto.response.IssueIncidentLinkResponse;
import hcmute.edu.zentech.exception.IssueIncidentConflictException;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.service.AdminIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/incidents")
@RequiredArgsConstructor
@Slf4j
public class AdminIncidentController {

    private final AdminIncidentService incidentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponseDto>>> getIncidents(
            @RequestParam(value = "status", required = false) IncidentStatus status,
            @RequestParam(value = "severity", required = false) IncidentSeverity severity,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        log.info("Request to get incidents, status={}, page={}, size={}", status, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("status"),
                Sort.Order.desc("createdAt")
        ));
        Page<IncidentResponseDto> incidentPage = incidentService.getIncidents(
                status, severity, assignee, startDate, endDate, search, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(incidentPage, incidentPage.getContent())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponseDto>> getIncidentById(@PathVariable("id") UUID id) {
        log.info("Request to get incident by id: {}", id);
        return ResponseEntity.ok(ApiResponse.success(incidentService.getIncidentById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IncidentResponseDto>> createIncident(
            @Valid @RequestBody IncidentCreateRequest request
    ) {
        log.info("Request to manually create incident");
        return ResponseEntity.ok(ApiResponse.success(incidentService.createIncident(request)));
    }


    @PostMapping("/from-issue")
    public ResponseEntity<ApiResponse<IncidentResponseDto>> createIncidentFromIssue(
            @Valid @RequestBody IncidentCreateFromIssueRequest request
    ) {
        log.info("Request to manually create incident from issue signature={}", request.getIssueSignature());
        try {
            return ResponseEntity.ok(ApiResponse.success(incidentService.createIncidentFromIssue(request)));
        } catch (IssueIncidentConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<IncidentResponseDto>builder()
                    .success(false)
                    .data(ex.getIncident())
                    .message(ex.getMessage())
                    .build());
        }
    }

    @PostMapping("/issue-links")
    public ResponseEntity<ApiResponse<Map<String, IssueIncidentLinkResponse>>> getIssueIncidentLinks(
            @Valid @RequestBody IssueLinkLookupRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(incidentService.findIssueLinks(request.getSignatures())));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<IncidentResponseDto>> updateIncident(
            @PathVariable("id") UUID id,
            @RequestBody IncidentUpdateRequest request
    ) {
        log.info("Request to update incident status/severity/assignee for id: {}", id);
        return ResponseEntity.ok(ApiResponse.success(incidentService.updateIncident(id, request)));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<AiAnalysisResponseDto>> analyzeIncident(@PathVariable("id") UUID id) {
        log.info("Request to analyze incident: {}", id);
        return ResponseEntity.ok(ApiResponse.success(incidentService.analyzeIncident(id)));
    }
}

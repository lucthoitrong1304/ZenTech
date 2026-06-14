package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.ActivityLogRecordRequest;
import hcmute.edu.zentech.dto.response.ActivityLogResponseDto;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.AdminActivityLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/activity-logs")
@RequiredArgsConstructor
@Slf4j
public class AdminActivityLogController {

    private final AdminActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponseDto>>> getActivityLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "search", required = false, defaultValue = "") String search
    ) {
        log.info("Request query activity logs received. page={}, size={}, search={}", page, size, search);
        return ResponseEntity.ok(ApiResponse.success(
                activityLogService.getActivityLogs(page, size, search)
        ));
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse<Void>> recordActivityLog(
            @Valid @RequestBody ActivityLogRecordRequest request
    ) {
        activityLogService.log(
                SecurityContextUtils.getCurrentUserId(),
                request.getArea(),
                request.getModule(),
                request.getAction(),
                request.getSeverity(),
                request.getTargetType(),
                request.getTargetId(),
                request.getTargetLabel(),
                request.getSummary(),
                request.getMetadata()
        );
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

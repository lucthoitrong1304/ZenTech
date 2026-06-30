package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.ActivityLogRecordRequest;
import hcmute.edu.zentech.dto.request.ActivityTimelineSummaryRequest;
import hcmute.edu.zentech.dto.response.ActivityLogResponseDto;
import hcmute.edu.zentech.dto.response.ActivityTimelineSummaryResponse;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.service.AdminActivityLogService;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import java.time.Instant;
import java.util.UUID;

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
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "area", required = false) ActivityArea area,
            @RequestParam(value = "severity", required = false) ActivitySeverity severity,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "action", required = false) ActivityAction action,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        log.info("Request query activity logs received. page={}, size={}, search={}, area={}, severity={}, module={}, action={}, from={}, to={}",
                page, size, search, area, severity, module, action, from, to);
        return ResponseEntity.ok(ApiResponse.success(
                activityLogService.getActivityLogs(page, size, search, area, severity, module, action, from, to)
        ));
    }

    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponseDto>>> getActivityTimeline(
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "email", required = false, defaultValue = "") String email,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "severity", required = false) ActivitySeverity severity,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "action", required = false) ActivityAction action
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                activityLogService.getActivityTimeline(userId, email, from, to, page, size, severity, module, action)
        ));
    }

    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<java.util.List<String>>> getDistinctModules() {
        return ResponseEntity.ok(ApiResponse.success(activityLogService.getDistinctModules()));
    }

    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<java.util.List<ActivityAction>>> getDistinctActions() {
        return ResponseEntity.ok(ApiResponse.success(activityLogService.getDistinctActions()));
    }

    @PostMapping("/timeline/summary")
    public ResponseEntity<ApiResponse<ActivityTimelineSummaryResponse>> summarizeTimeline(
            @RequestBody ActivityTimelineSummaryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(activityLogService.summarizeTimeline(request)));
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

    @PostMapping("/recordings")
    public ResponseEntity<ApiResponse<Void>> saveRecording(
            @RequestParam("email") String email,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestBody List<Object> events
    ) {
        CustomUserDetails currentUser = SecurityContextUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !currentUser.getEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(403).build();
        }

        int eventCount = events == null ? 0 : events.size();
        if (eventCount == 0) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        if (eventCount > AdminActivityLogService.MAX_RECORDING_EVENTS_PER_CHUNK) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Recording chunk exceeds the allowed event limit")
                            .build());
        }

        try {
            log.info("Received {} rrweb events to save for {} session {}", eventCount, email, sessionId);
            activityLogService.saveRecording(email, sessionId, events);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message(ex.getMessage())
                            .build());
        }
    }

    @GetMapping("/recordings")
    public ResponseEntity<ApiResponse<List<Object>>> getRecording(
            @RequestParam("email") String email,
            @RequestParam(value = "sessionId", required = false) String sessionId
    ) {
        log.info("Request get rrweb recording for {} session {}", email, sessionId);
        return ResponseEntity.ok(ApiResponse.success(activityLogService.getRecording(email, sessionId)));
    }

    @GetMapping("/recordings/sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRecordingSessions(
            @RequestParam("email") String email
    ) {
        log.info("Request get rrweb sessions list for {}", email);
        return ResponseEntity.ok(ApiResponse.success(activityLogService.getRecordingSessions(email)));
    }

    @DeleteMapping("/recordings")
    public ResponseEntity<ApiResponse<Void>> deleteRecording(
            @RequestParam("email") String email,
            @RequestParam(value = "sessionId", required = false) String sessionId
    ) {
        log.info("Request delete rrweb recording for {} session {}", email, sessionId);
        activityLogService.deleteRecording(email, sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

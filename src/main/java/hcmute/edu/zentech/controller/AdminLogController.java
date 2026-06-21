package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AdminLogController {

    private final AdminLogService adminLogService;

    @GetMapping("/admin/logs")
    public ResponseEntity<List<Map<String, Object>>> getLogs(
            @RequestParam(value = "level", required = false, defaultValue = "ALL") String level,
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "traceId", required = false, defaultValue = "") String traceId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime
    ) {
        log.info("Request query logs received. level={}, search={}, traceId={}, startTime={}, endTime={}", level, search, traceId, startTime, endTime);
        List<Map<String, Object>> logs = adminLogService.getLogs(level, search, traceId, limit, startTime, endTime);
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/logs/client")
    public ResponseEntity<Map<String, Object>> recordClientLog(@RequestBody Map<String, Object> logPayload) {
        adminLogService.writeClientLog(logPayload);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Nhật ký lỗi client đã được ghi nhận."
        ));
    }

    @PostMapping("/admin/logs/explain")
    public ResponseEntity<Map<String, Object>> explainLog(@RequestBody Map<String, Object> logPayload) {
        log.info("Request to explain log received");
        Map<String, Object> result = adminLogService.explainLogError(logPayload);
        return ResponseEntity.ok(result);
    }
}

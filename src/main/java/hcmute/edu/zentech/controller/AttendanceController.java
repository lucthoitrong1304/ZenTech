package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.request.AttendanceReportPreferenceRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.EmployeeProfileResponse;
import hcmute.edu.zentech.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import hcmute.edu.zentech.dto.response.AttendanceReportResponse;
import hcmute.edu.zentech.dto.response.AttendanceReportPreferenceResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<EmployeeProfileResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request
    ) {
        EmployeeProfileResponse employee = attendanceService.checkIn(request);
        return ResponseEntity.ok(ApiResponse.<EmployeeProfileResponse>builder()
                .success(true)
                .data(employee)
                .message("Chấm công thành công. Xin chào " + employee.getFullName())
                .build());
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<AttendanceReportResponse>> getAttendanceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size
    ) {
        AttendanceReportResponse report = attendanceService.getAttendanceReport(startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/report/preferences")
    public ResponseEntity<ApiResponse<AttendanceReportPreferenceResponse>> getReportPreference() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getReportPreference()));
    }

    @PutMapping("/report/preferences")
    public ResponseEntity<ApiResponse<AttendanceReportPreferenceResponse>> saveReportPreference(@RequestBody AttendanceReportPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.saveReportPreference(request.getVisibleMetrics())));
    }
}

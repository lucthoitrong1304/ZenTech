package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.shift.*;
import hcmute.edu.zentech.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {
    private final ShiftService shiftService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShiftDto>> createShift(@Valid @RequestBody ShiftCreateDto dto) {
        return ResponseEntity.status(201).body(ApiResponse.success(shiftService.createShift(dto)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShiftDto>>> getAllShifts() {
        return ResponseEntity.ok(ApiResponse.success(shiftService.getAllShifts()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<List<ShiftDto>>> updateShifts(@RequestBody List<ShiftDto> updateDtos) {
        return ResponseEntity.ok(ApiResponse.success(shiftService.updateShifts(updateDtos)));
    }

    @GetMapping("/schedules")
    public ResponseEntity<ApiResponse<WeeklyScheduleResponse>> getWeeklySchedules(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        Page<EmployeeWeeklyScheduleDto> schedules = shiftService.getWeeklySchedules(startDate, endDate, keyword, pageable);
        WeeklyScheduleResponse response = new WeeklyScheduleResponse(schedules);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/schedules")
    public ResponseEntity<ApiResponse<Void>> assignSingleShift(@Valid @RequestBody EmployeeShiftDto dto) {
        shiftService.assignSingleShift(dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/schedules/bulk")
    public ResponseEntity<ApiResponse<Void>> bulkAssignShifts(@Valid @RequestBody BulkShiftUpdateDto dto) {
        shiftService.bulkAssignShifts(dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/schedules/copy-week")
    public ResponseEntity<ApiResponse<Void>> copyWeeklySchedule(@Valid @RequestBody CopyWeekDto dto) {
        shiftService.copyWeeklySchedule(dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/schedules/{employeeShiftId}")
    public ResponseEntity<ApiResponse<Void>> deleteScheduleAssignment(
            @PathVariable UUID employeeShiftId,
            @RequestParam(required = false) String reason
    ) {
        shiftService.deleteScheduleAssignment(employeeShiftId, reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/my-schedules")
    public ResponseEntity<ApiResponse<List<EmployeeWeeklyScheduleDto.DailyShiftDto>>> getMyDailyShifts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(shiftService.getMyDailyShifts(startDate, endDate)));
    }
}

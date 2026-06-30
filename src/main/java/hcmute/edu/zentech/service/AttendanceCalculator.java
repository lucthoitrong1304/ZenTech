package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AttendanceEventTimelineResponse;
import hcmute.edu.zentech.dto.response.AttendanceRecordResponse;
import hcmute.edu.zentech.dto.response.AttendanceShiftBreakdownResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceCalculator {
    private final EmployeeShiftRepository employeeShiftRepository;
    private final ScheduleAdjustmentRepository scheduleAdjustmentRepository;
    private final ShiftSwapRequestRepository shiftSwapRequestRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final AttendanceAdjustmentRepository attendanceAdjustmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final R2StorageService r2StorageService;

    public Shift resolveEffectiveShift(UUID employeeId, LocalDate date) {
        return resolveEffectiveShifts(employeeId, date).stream()
                .map(EffectiveShift::shift)
                .findFirst()
                .orElse(null);
    }

    public List<EffectiveShift> resolveEffectiveShifts(UUID employeeId, LocalDate date) {
        List<EffectiveShift> candidates = getRawEffectiveShifts(employeeId, date);
        List<LeaveRequest> approvedLeaves = leaveRequestRepository
                .findLeavesForEmployeeInRangeWithStatuses(employeeId, date, date, List.of(ApprovalStatus.APPROVED, ApprovalStatus.CANCEL_PENDING));

        List<EffectiveShift> result = new ArrayList<>();
        for (EffectiveShift candidate : candidates) {
            boolean isLeave = false;
            boolean isWfh = false;
            boolean isAfk = false;
            LeaveRequest leaveReq = null;

            if (approvedLeaves != null) {
                for (LeaveRequest leave : approvedLeaves) {
                    boolean coversShift = false;
                    if (leave.getTargetShifts() == null || leave.getTargetShifts().isEmpty()) {
                        coversShift = true;
                    } else {
                        coversShift = leave.getTargetShifts().stream()
                                .anyMatch(s -> s.getId().equals(candidate.shift().getId()));
                    }

                    if (coversShift) {
                        String code = leave.getLeaveType().getCode();
                        if ("NGHI".equals(code)) {
                            isLeave = true;
                            leaveReq = leave;
                        } else if ("WFH".equals(code)) {
                            isWfh = true;
                            leaveReq = leave;
                        } else if ("AFK".equals(code)) {
                            isAfk = true;
                            leaveReq = leave;
                        }
                    }
                }
            }

            String desc = candidate.changeDescription();
            Shift orig = candidate.originalShift();
            if (isLeave) {
                orig = candidate.shift();
                desc = "Nghỉ phép được duyệt: " + (leaveReq.getReason() != null ? leaveReq.getReason() : "");
            } else if (isWfh) {
                orig = candidate.shift();
                desc = "WFH được duyệt: " + (leaveReq.getReason() != null ? leaveReq.getReason() : "");
            } else if (isAfk) {
                orig = candidate.shift();
                desc = "Đi muộn/về sớm được duyệt: " + (leaveReq.getReason() != null ? leaveReq.getReason() : "");
            }

            result.add(new EffectiveShift(
                    candidate.assignment(),
                    candidate.shift(),
                    isLeave,
                    isWfh,
                    isAfk,
                    candidate.isSwap(),
                    leaveReq,
                    orig,
                    desc
            ));
        }

        return result;
    }

    private List<EffectiveShift> getRawEffectiveShifts(UUID employeeId, LocalDate date) {
        List<ScheduleAdjustment> adjustments = scheduleAdjustmentRepository
                .findByEmployeeIdAndWorkDateBetween(employeeId, date, date);
        List<EffectiveShift> approvedAdjustments = adjustments.stream()
                .filter(sa -> sa.getStatus() == ApprovalStatus.APPROVED)
                .map(sa -> {
                    Shift adjusted = sa.getAdjustedShift();
                    if (adjusted == null) {
                        adjusted = new Shift();
                        adjusted.setName("Nghỉ (Đã xóa ca)");
                        adjusted.setType(ShiftType.OFF);
                        adjusted.setColorCode("#6B7280");
                    }
                    return new EffectiveShift(
                            null,
                            adjusted,
                            false,
                            false,
                            false,
                            false,
                            null,
                            sa.getOriginalShift(),
                            "Điều chỉnh lịch: " + sa.getReason()
                    );
                })
                .sorted(Comparator.comparing(item -> item.shift() != null ? item.shift().getStartTime() : null, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (!approvedAdjustments.isEmpty()) {
            return approvedAdjustments;
        }

        List<ShiftSwapRequest> swapRequests = shiftSwapRequestRepository
                .findSwapsForEmployeeInRangeWithStatuses(employeeId, date, date, List.of(ApprovalStatus.APPROVED, ApprovalStatus.CANCEL_PENDING));
        for (ShiftSwapRequest req : swapRequests) {
            List<EffectiveShift> swapResult = resolveSwap(employeeId, date, req);
            if (swapResult != null) {
                return swapResult;
            }
        }

        return employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, date).stream()
                .map(assignment -> new EffectiveShift(assignment, assignment.getShift()))
                .filter(item -> item.shift() != null)
                .filter(item -> item.shift().getType() != ShiftType.OFF)
                .sorted(Comparator.comparing(item -> item.shift().getStartTime(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public AttendanceRecordResponse calculateDayAttendance(Employee employee, LocalDate date) {
        List<EffectiveShift> shifts = resolveEffectiveShifts(employee.getId(), date);
        String shiftName = shifts.isEmpty()
                ? "Nghỉ"
                : shifts.stream()
                .map(item -> item.shift().getName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<AttendanceEvent> rawEvents = attendanceEventRepository
                .findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(employee.getId(), startOfDay, endOfDay);
        List<AttendanceAdjustment> adjustments = attendanceAdjustmentRepository
                .findByEmployeeIdAndWorkDateBetweenAndStatusIn(employee.getId(), date, date, List.of(ApprovalStatus.APPROVED, ApprovalStatus.CANCEL_PENDING));

        List<LocalDateTime> detailTimes = uniqueTimes(rawEvents.stream()
                .map(AttendanceEvent::getTimestamp)
                .collect(Collectors.toCollection(ArrayList::new)));

        List<AttendanceShiftBreakdownResponse> breakdowns = new ArrayList<>();
        if (!shifts.isEmpty()) {
            List<LeaveRequest> approvedLeaves = leaveRequestRepository
                    .findLeavesForEmployeeInRangeWithStatuses(employee.getId(), date, date, List.of(ApprovalStatus.APPROVED, ApprovalStatus.CANCEL_PENDING));
            if (approvedLeaves == null) {
                approvedLeaves = Collections.emptyList();
            }
            for (EffectiveShift effectiveShift : shifts) {
                breakdowns.add(calculateShiftBreakdown(effectiveShift, rawEvents, adjustments, approvedLeaves, date));
            }
        }

        double totalHours = breakdowns.stream().mapToDouble(AttendanceShiftBreakdownResponse::getWorkingHours).sum();
        long lateMinutes = breakdowns.stream().mapToLong(AttendanceShiftBreakdownResponse::getLateMinutes).sum();
        long earlyMinutes = breakdowns.stream().mapToLong(AttendanceShiftBreakdownResponse::getEarlyMinutes).sum();
        boolean provisional = breakdowns.stream().anyMatch(AttendanceShiftBreakdownResponse::isProvisional);

        LocalDateTime firstCheckIn = breakdowns.stream()
                .map(AttendanceShiftBreakdownResponse::getCheckInTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastCheckOut = breakdowns.stream()
                .map(AttendanceShiftBreakdownResponse::getCheckOutTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        String status = shifts.isEmpty()
                ? "OFF"
                : combineDayStatus(breakdowns.stream()
                .map(AttendanceShiftBreakdownResponse::getStatus)
                .collect(Collectors.toCollection(ArrayList::new)));

        return AttendanceRecordResponse.builder()
                .id(UUID.randomUUID())
                .employeeId(employee.getId())
                .employeeName(employee.getFullName())
                .workDate(date)
                .shiftName(shiftName)
                .checkInTime(firstCheckIn)
                .checkOutTime(lastCheckOut)
                .workingHours(totalHours)
                .lateMinutes(lateMinutes)
                .earlyMinutes(earlyMinutes)
                .status(status)
                .detailTimes(detailTimes)
                .isProvisional(provisional)
                .shiftBreakdowns(breakdowns)
                .build();
    }
    public record EffectiveShift(
            EmployeeShift assignment,
            Shift shift,
            boolean isLeave,
            boolean isWfh,
            boolean isAfk,
            boolean isSwap,
            LeaveRequest leaveRequest,
            Shift originalShift,
            String changeDescription
    ) {
        public EffectiveShift(EmployeeShift assignment, Shift shift) {
            this(assignment, shift, false, false, false, false, null, null, null);
        }

        public EffectiveShift(EmployeeShift assignment, Shift shift, boolean isLeave, boolean isWfh, boolean isAfk, LeaveRequest leaveRequest) {
            this(assignment, shift, isLeave, isWfh, isAfk, false, leaveRequest, null, null);
        }

        public EffectiveShift(EmployeeShift assignment, Shift shift, boolean isLeave, boolean isWfh, boolean isAfk, LeaveRequest leaveRequest, Shift originalShift, String changeDescription) {
            this(assignment, shift, isLeave, isWfh, isAfk, false, leaveRequest, originalShift, changeDescription);
        }
    }
    private List<EffectiveShift> resolveSwap(UUID employeeId, LocalDate date, ShiftSwapRequest req) {
        if (req.getType() == SwapRequestType.COVER) {
            if (req.getTargetEmployee().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                return req.getShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getShift(), false, false, false, true, null, null, "Làm thay cho " + req.getRequester().getFullName()));
            }
            if (req.getRequester().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                Shift offShift = new Shift();
                offShift.setName("Nghỉ (Làm thay)");
                offShift.setType(ShiftType.OFF);
                offShift.setColorCode("#6B7280");
                return List.of(new EffectiveShift(null, offShift, false, false, false, true, null, req.getShift(), "Đồng nghiệp " + req.getTargetEmployee().getFullName() + " làm thay"));
            }
        }

        if (req.getType() != SwapRequestType.SWAP) {
            return null;
        }

        if (req.getWorkDate().equals(req.getTargetWorkDate())) {
            if (req.getRequester().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                return req.getTargetShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getTargetShift(), false, false, false, true, null, req.getShift(), "Đổi ca với " + req.getTargetEmployee().getFullName()));
            }
            if (req.getTargetEmployee().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                return req.getShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getShift(), false, false, false, true, null, req.getTargetShift(), "Đổi ca với " + req.getRequester().getFullName()));
            }
        } else {
            if (req.getWorkDate().equals(date)) {
                if (req.getRequester().getId().equals(employeeId)) {
                    Shift offShift = new Shift();
                    offShift.setName("Nghỉ (Đổi ca)");
                    offShift.setType(ShiftType.OFF);
                    offShift.setColorCode("#6B7280");
                    return List.of(new EffectiveShift(null, offShift, false, false, false, true, null, req.getShift(), "Đổi ca ngày " + req.getTargetWorkDate() + " với " + req.getTargetEmployee().getFullName()));
                }
                if (req.getTargetEmployee().getId().equals(employeeId)) {
                    return req.getShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getShift(), false, false, false, true, null, null, "Đổi ca với " + req.getRequester().getFullName()));
                }
            }
            if (req.getTargetWorkDate() != null && req.getTargetWorkDate().equals(date)) {
                if (req.getRequester().getId().equals(employeeId)) {
                    return req.getTargetShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getTargetShift(), false, false, false, true, null, null, "Đổi ca với " + req.getTargetEmployee().getFullName()));
                }
                if (req.getTargetEmployee().getId().equals(employeeId)) {
                    Shift offShift = new Shift();
                    offShift.setName("Nghỉ (Đổi ca)");
                    offShift.setType(ShiftType.OFF);
                    offShift.setColorCode("#6B7280");
                    return List.of(new EffectiveShift(null, offShift, false, false, false, true, null, req.getTargetShift(), "Đổi ca ngày " + req.getWorkDate() + " với " + req.getRequester().getFullName()));
                }
            }
        }

        return null;
    }

    private AttendanceShiftBreakdownResponse calculateShiftBreakdown(EffectiveShift effectiveShift,
                                                                     List<AttendanceEvent> rawEvents,
                                                                     List<AttendanceAdjustment> adjustments,
                                                                     List<LeaveRequest> approvedLeaves,
                                                                     LocalDate date) {
        Shift shift = effectiveShift.shift();
        List<TimeEntry> entries = uniqueEntries(resolveEntriesForShift(effectiveShift, rawEvents, adjustments, date));
        List<AttendanceEventTimelineResponse> events = entries.stream()
                .map(entry -> AttendanceEventTimelineResponse.builder()
                        .type(entry.type())
                        .timestamp(entry.timestamp())
                        .source(entry.source())
                        .faceImageUrl(entry.faceImageKey() != null ? r2StorageService.getPresignedGetUrl(entry.faceImageKey()) : null)
                        .build())
                .toList();

        LocalDateTime activeCheckIn = null;
        LocalDateTime firstCheckIn = null;
        LocalDateTime lastCheckOut = null;
        double workingHours = 0.0;
        boolean provisional = false;

        for (TimeEntry entry : entries) {
            if ("CHECK_IN".equals(entry.type())) {
                if (firstCheckIn == null) {
                    firstCheckIn = entry.timestamp();
                }
                activeCheckIn = entry.timestamp();
            } else if ("CHECK_OUT".equals(entry.type())) {
                if (activeCheckIn != null) {
                    workingHours += Math.max(0.0, Duration.between(activeCheckIn, entry.timestamp()).toMinutes() / 60.0);
                    activeCheckIn = null;
                }
                lastCheckOut = entry.timestamp();
            }
        }

        if (activeCheckIn != null) {
            if (date.equals(LocalDate.now())) {
                workingHours += Math.max(0.0, Duration.between(activeCheckIn, LocalDateTime.now()).toMinutes() / 60.0);
                provisional = true;
            }
        }

        LocalDateTime checkIn = firstCheckIn;
        LocalDateTime checkOut = lastCheckOut;
        long lateMinutes = 0;
        long earlyMinutes = 0;
        String status;

        if (checkIn == null) {
            if (effectiveShift.isLeave() || !approvedLeaves.isEmpty()) {
                status = "ABSENT_EXCUSED";
            } else if (date.equals(LocalDate.now()) && !hasShiftEnded(shift, LocalTime.now())) {
                status = "NOT_STARTED";
            } else {
                status = "ABSENT_UNEXCUSED";
            }
        } else {
            String inStatus = classifyCheckIn(shift, checkIn.toLocalTime());
            if ("LATE".equals(inStatus) && shift.getStartTime() != null) {
                LocalTime onTimeEnd = shift.getStartTime().plusMinutes(defaultInt(shift.getOnTimeCheckInEndMinutes(), 5));
                lateMinutes = Math.max(0, Duration.between(onTimeEnd, checkIn.toLocalTime()).toMinutes());
            }

            if (checkOut == null) {
                status = effectiveShift.isWfh() ? "WFH_MISSING_CHECK_OUT" : "MISSING_CHECK_OUT";
            } else {
                String outStatus = classifyCheckOut(shift, checkOut.toLocalTime());
                if ("EARLY_CHECKOUT".equals(outStatus) && shift.getEndTime() != null) {
                    LocalTime onTimeStart = shift.getEndTime().minusMinutes(defaultInt(shift.getOnTimeCheckOutStartMinutes(), 5));
                    earlyMinutes = Math.max(0, Duration.between(checkOut.toLocalTime(), onTimeStart).toMinutes());
                }
                status = combineShiftStatus(inStatus, outStatus);
                if (effectiveShift.isWfh()) {
                    status = "WFH_" + status;
                }
            }
        }

        double afkHours = 0.0;
        if (effectiveShift.isAfk() && effectiveShift.leaveRequest() != null) {
            LeaveRequest afkReq = effectiveShift.leaveRequest();
            if (afkReq.getStartTime() != null && afkReq.getEndTime() != null) {
                afkHours = Duration.between(afkReq.getStartTime(), afkReq.getEndTime()).toMinutes() / 60.0;
                workingHours = Math.max(0.0, workingHours - afkHours);
            }
        }

        return AttendanceShiftBreakdownResponse.builder()
                .shiftId(shift.getId())
                .employeeShiftId(effectiveShift.assignment() == null ? null : effectiveShift.assignment().getId())
                .shiftName(shift.getName())
                .scheduledStartTime(shift.getStartTime())
                .scheduledEndTime(shift.getEndTime())
                .checkInTime(checkIn)
                .checkOutTime(checkOut)
                .workingHours(workingHours)
                .lateMinutes(lateMinutes)
                .earlyMinutes(earlyMinutes)
                .status(status)
                .isProvisional(provisional)
                .isLeave(effectiveShift.isLeave())
                .isWfh(effectiveShift.isWfh())
                .isAfk(effectiveShift.isAfk())
                .isSwap(effectiveShift.isSwap())
                .originalShiftName(effectiveShift.originalShift() != null ? effectiveShift.originalShift().getName() : null)
                .changeDescription(effectiveShift.changeDescription())
                .afkHours(afkHours)
                .events(events)
                .build();
    }

    private List<TimeEntry> resolveEntriesForShift(EffectiveShift effectiveShift,
                                                   List<AttendanceEvent> rawEvents,
                                                   List<AttendanceAdjustment> adjustments,
                                                   LocalDate date) {
        Shift shift = effectiveShift.shift();
        UUID assignmentId = effectiveShift.assignment() == null ? null : effectiveShift.assignment().getId();
        List<TimeEntry> entries = new ArrayList<>();

        for (AttendanceEvent event : rawEvents) {
            if (assignmentId != null && event.getEmployeeShift() != null
                    && assignmentId.equals(event.getEmployeeShift().getId())) {
                entries.add(new TimeEntry(event.getTimestamp(), event.getEventType().name(), event.getSource(), event.getFaceImageKey()));
            } else if (event.getEmployeeShift() == null && isWithinShiftCaptureRange(event.getTimestamp().toLocalTime(), shift)) {
                entries.add(new TimeEntry(event.getTimestamp(), event.getEventType().name(), event.getSource(), event.getFaceImageKey()));
            }
        }

        for (AttendanceAdjustment adjustment : adjustments) {
            if (isWithinShiftCaptureRange(adjustment.getProposedTime(), shift)) {
                entries.add(new TimeEntry(LocalDateTime.of(date, adjustment.getProposedTime()), adjustment.getType().name(), "APPROVED_ADJUSTMENT", null));
            }
        }

        return entries;
    }

    private String classifyCheckIn(Shift shift, LocalTime checkIn) {
        if (shift.getType() == ShiftType.OFF || shift.getStartTime() == null) {
            return "ON_TIME";
        }
        LocalTime onTimeStart = shift.getStartTime().minusMinutes(defaultInt(shift.getOnTimeCheckInStartMinutes(), 15));
        LocalTime onTimeEnd = shift.getStartTime().plusMinutes(defaultInt(shift.getOnTimeCheckInEndMinutes(), 5));
        if (checkIn.isBefore(onTimeStart)) {
            return "EARLY_CHECKIN";
        }
        if (checkIn.isAfter(onTimeEnd)) {
            return "LATE";
        }
        return "ON_TIME";
    }

    private String classifyCheckOut(Shift shift, LocalTime checkOut) {
        if (shift.getType() == ShiftType.OFF || shift.getEndTime() == null) {
            return "ON_TIME";
        }
        LocalTime onTimeStart = shift.getEndTime().minusMinutes(defaultInt(shift.getOnTimeCheckOutStartMinutes(), 5));
        LocalTime onTimeEnd = shift.getEndTime().plusMinutes(defaultInt(shift.getOnTimeCheckOutEndMinutes(), 15));
        if (checkOut.isBefore(onTimeStart)) {
            return "EARLY_CHECKOUT";
        }
        if (checkOut.isAfter(onTimeEnd)) {
            return "LATE_CHECKOUT";
        }
        return "ON_TIME";
    }

    private boolean isWithinShiftCaptureRange(LocalTime time, Shift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return true;
        }
        LocalTime start = shift.getStartTime().minusMinutes(defaultInt(shift.getEarlyCheckInMinutes(), 30));
        LocalTime end = shift.getEndTime().plusMinutes(defaultInt(shift.getLateCheckOutMinutes(), 60));
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private boolean hasShiftEnded(Shift shift, LocalTime now) {
        if (shift.getEndTime() == null) {
            return false;
        }
        LocalTime captureEnd = shift.getEndTime().plusMinutes(defaultInt(shift.getLateCheckOutMinutes(), 60));
        return now.isAfter(captureEnd);
    }

    private List<LocalDateTime> uniqueTimes(List<LocalDateTime> times) {
        Collections.sort(times);
        List<LocalDateTime> uniqueTimes = new ArrayList<>();
        for (LocalDateTime t : times) {
            if (uniqueTimes.isEmpty()) {
                uniqueTimes.add(t);
                continue;
            }
            LocalDateTime lastAdded = uniqueTimes.get(uniqueTimes.size() - 1);
            if (Duration.between(lastAdded, t).abs().toSeconds() > 10) {
                uniqueTimes.add(t);
            }
        }
        return uniqueTimes;
    }

    private List<TimeEntry> uniqueEntries(List<TimeEntry> entries) {
        entries.sort(Comparator.comparing(TimeEntry::timestamp));
        List<TimeEntry> uniqueEntries = new ArrayList<>();
        for (TimeEntry entry : entries) {
            if (uniqueEntries.isEmpty()) {
                uniqueEntries.add(entry);
                continue;
            }
            TimeEntry lastAdded = uniqueEntries.get(uniqueEntries.size() - 1);
            if (Duration.between(lastAdded.timestamp(), entry.timestamp()).abs().toSeconds() > 10) {
                uniqueEntries.add(entry);
            }
        }
        return uniqueEntries;
    }

    private String combineShiftStatus(String inStatus, String outStatus) {
        boolean late = "LATE".equals(inStatus);
        boolean early = "EARLY_CHECKOUT".equals(outStatus);
        if (late && early) {
            return "LATE_AND_EARLY";
        }
        if (late) {
            return "LATE";
        }
        if (early) {
            return "EARLY_CHECKOUT";
        }
        if ("EARLY_CHECKIN".equals(inStatus)) {
            return "EARLY_CHECKIN";
        }
        if ("LATE_CHECKOUT".equals(outStatus)) {
            return "LATE_CHECKOUT";
        }
        return "ON_TIME";
    }

    private String combineDayStatus(List<String> statuses) {
        if (statuses.contains("MISSING_CHECK_OUT")) {
            return "MISSING_CHECK_OUT";
        }
        if (statuses.stream().allMatch("NOT_STARTED"::equals)) {
            return "NOT_STARTED";
        }

        List<String> workedStatuses = statuses.stream()
                .filter(status -> !"NOT_STARTED".equals(status))
                .filter(status -> !"ABSENT_EXCUSED".equals(status))
                .filter(status -> !"ABSENT_UNEXCUSED".equals(status))
                .toList();

        if (workedStatuses.isEmpty()) {
            if (statuses.contains("ABSENT_UNEXCUSED")) {
                return "ABSENT_UNEXCUSED";
            }
            if (statuses.contains("ABSENT_EXCUSED")) {
                return "ABSENT_EXCUSED";
            }
            return "NOT_STARTED";
        }

        if (workedStatuses.contains("LATE_AND_EARLY")
                || (workedStatuses.contains("LATE") && workedStatuses.contains("EARLY_CHECKOUT"))) {
            return "LATE_AND_EARLY";
        }
        if (workedStatuses.contains("LATE")) {
            return "LATE";
        }
        if (workedStatuses.contains("EARLY_CHECKOUT")) {
            return "EARLY_CHECKOUT";
        }
        if (workedStatuses.contains("EARLY_CHECKIN")) {
            return "EARLY_CHECKIN";
        }
        if (workedStatuses.contains("LATE_CHECKOUT")) {
            return "LATE_CHECKOUT";
        }
        return "ON_TIME";
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }

    private record TimeEntry(LocalDateTime timestamp, String type, String source, String faceImageKey) {
    }
}

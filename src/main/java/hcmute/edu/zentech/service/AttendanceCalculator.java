package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AttendanceRecordResponse;
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

    public Shift resolveEffectiveShift(UUID employeeId, LocalDate date) {
        return resolveEffectiveShifts(employeeId, date).stream()
                .map(EffectiveShift::shift)
                .findFirst()
                .orElse(null);
    }

    public List<EffectiveShift> resolveEffectiveShifts(UUID employeeId, LocalDate date) {
        List<ScheduleAdjustment> adjustments = scheduleAdjustmentRepository
                .findByEmployeeIdAndWorkDateBetween(employeeId, date, date);
        List<EffectiveShift> approvedAdjustments = adjustments.stream()
                .filter(sa -> sa.getStatus() == ApprovalStatus.APPROVED)
                .map(ScheduleAdjustment::getAdjustedShift)
                .filter(Objects::nonNull)
                .filter(shift -> shift.getType() != ShiftType.OFF)
                .map(shift -> new EffectiveShift(null, shift))
                .sorted(Comparator.comparing(item -> item.shift().getStartTime(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (!approvedAdjustments.isEmpty()) {
            return approvedAdjustments;
        }

        List<ShiftSwapRequest> swapRequests = shiftSwapRequestRepository
                .findApprovedSwapsForEmployeeInRange(employeeId, date, date, ApprovalStatus.APPROVED);
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
                .findByEmployeeIdAndWorkDateBetweenAndStatus(employee.getId(), date, date, ApprovalStatus.APPROVED);

        List<LocalDateTime> detailTimes = uniqueTimes(rawEvents.stream()
                .map(AttendanceEvent::getTimestamp)
                .collect(Collectors.toCollection(ArrayList::new)));

        double totalHours = 0.0;
        LocalDateTime firstCheckIn = null;
        LocalDateTime lastCheckOut = null;
        long lateMinutes = 0;
        long earlyMinutes = 0;
        List<String> shiftStatuses = new ArrayList<>();
        String status = "OFF";

        if (!shifts.isEmpty()) {
            for (EffectiveShift effectiveShift : shifts) {
                Shift shift = effectiveShift.shift();
                List<LocalDateTime> shiftTimes = uniqueTimes(resolveTimesForShift(effectiveShift, rawEvents, adjustments, date));

                if (shiftTimes.isEmpty()) {
                    shiftStatuses.add("ABSENT_UNEXCUSED");
                    continue;
                }

                LocalDateTime checkIn = shiftTimes.get(0);
                LocalDateTime checkOut = shiftTimes.size() > 1 ? shiftTimes.get(shiftTimes.size() - 1) : null;

                if (firstCheckIn == null || checkIn.isBefore(firstCheckIn)) {
                    firstCheckIn = checkIn;
                }
                if (checkOut != null && (lastCheckOut == null || checkOut.isAfter(lastCheckOut))) {
                    lastCheckOut = checkOut;
                    totalHours += Math.max(0.0, Duration.between(checkIn, checkOut).toMinutes() / 60.0);
                }

                String inStatus = classifyCheckIn(shift, checkIn.toLocalTime());
                if ("LATE".equals(inStatus) && shift.getStartTime() != null) {
                    LocalTime onTimeEnd = shift.getStartTime().plusMinutes(defaultInt(shift.getOnTimeCheckInEndMinutes(), 5));
                    lateMinutes += Math.max(0, Duration.between(onTimeEnd, checkIn.toLocalTime()).toMinutes());
                }

                if (checkOut == null) {
                    shiftStatuses.add("MISSING_CHECK_OUT");
                    continue;
                }

                String outStatus = classifyCheckOut(shift, checkOut.toLocalTime());
                if ("EARLY_CHECKOUT".equals(outStatus) && shift.getEndTime() != null) {
                    LocalTime onTimeStart = shift.getEndTime().minusMinutes(defaultInt(shift.getOnTimeCheckOutStartMinutes(), 5));
                    earlyMinutes += Math.max(0, Duration.between(checkOut.toLocalTime(), onTimeStart).toMinutes());
                }

                shiftStatuses.add(combineShiftStatus(inStatus, outStatus));
            }

            if (firstCheckIn == null) {
                List<LeaveRequest> leaves = leaveRequestRepository
                        .findApprovedLeavesForEmployeeInRange(employee.getId(), date, date, ApprovalStatus.APPROVED);
                status = leaves.isEmpty() ? "ABSENT_UNEXCUSED" : "ABSENT_EXCUSED";
            } else {
                status = combineDayStatus(shiftStatuses);
            }
        }

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
                .build();
    }

    public record EffectiveShift(EmployeeShift assignment, Shift shift) {
    }

    private List<EffectiveShift> resolveSwap(UUID employeeId, LocalDate date, ShiftSwapRequest req) {
        if (req.getType() == SwapRequestType.COVER) {
            if (req.getTargetEmployee().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                return req.getShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getShift()));
            }
            if (req.getRequester().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                return List.of();
            }
        }

        if (req.getType() != SwapRequestType.SWAP) {
            return null;
        }

        if (req.getWorkDate().equals(req.getTargetWorkDate())) {
            if (req.getRequester().getId().equals(employeeId)) {
                return req.getTargetShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getTargetShift()));
            }
            if (req.getTargetEmployee().getId().equals(employeeId)) {
                return req.getShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getShift()));
            }
        } else {
            if (req.getWorkDate().equals(date)) {
                if (req.getRequester().getId().equals(employeeId)) {
                    return List.of();
                }
                if (req.getTargetEmployee().getId().equals(employeeId)) {
                    return req.getShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getShift()));
                }
            }
            if (req.getTargetWorkDate() != null && req.getTargetWorkDate().equals(date)) {
                if (req.getRequester().getId().equals(employeeId)) {
                    return req.getTargetShift() == null ? List.of() : List.of(new EffectiveShift(null, req.getTargetShift()));
                }
                if (req.getTargetEmployee().getId().equals(employeeId)) {
                    return List.of();
                }
            }
        }

        return null;
    }

    private List<LocalDateTime> resolveTimesForShift(EffectiveShift effectiveShift,
                                                     List<AttendanceEvent> rawEvents,
                                                     List<AttendanceAdjustment> adjustments,
                                                     LocalDate date) {
        Shift shift = effectiveShift.shift();
        UUID assignmentId = effectiveShift.assignment() == null ? null : effectiveShift.assignment().getId();
        List<LocalDateTime> times = new ArrayList<>();

        for (AttendanceEvent event : rawEvents) {
            if (assignmentId != null && event.getEmployeeShift() != null
                    && assignmentId.equals(event.getEmployeeShift().getId())) {
                times.add(event.getTimestamp());
            } else if (event.getEmployeeShift() == null && isWithinShiftCaptureRange(event.getTimestamp().toLocalTime(), shift)) {
                times.add(event.getTimestamp());
            }
        }

        for (AttendanceAdjustment adjustment : adjustments) {
            if (isWithinShiftCaptureRange(adjustment.getProposedTime(), shift)) {
                times.add(LocalDateTime.of(date, adjustment.getProposedTime()));
            }
        }

        return times;
    }

    private String classifyCheckIn(Shift shift, LocalTime checkIn) {
        if (shift.getStartTime() == null) {
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
        if (shift.getEndTime() == null) {
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
        if (statuses.contains("ABSENT_UNEXCUSED")) {
            return "ABSENT_UNEXCUSED";
        }
        if (statuses.contains("LATE_AND_EARLY")
                || (statuses.contains("LATE") && statuses.contains("EARLY_CHECKOUT"))) {
            return "LATE_AND_EARLY";
        }
        if (statuses.contains("LATE")) {
            return "LATE";
        }
        if (statuses.contains("EARLY_CHECKOUT")) {
            return "EARLY_CHECKOUT";
        }
        if (statuses.contains("EARLY_CHECKIN")) {
            return "EARLY_CHECKIN";
        }
        if (statuses.contains("LATE_CHECKOUT")) {
            return "LATE_CHECKOUT";
        }
        return "ON_TIME";
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }
}

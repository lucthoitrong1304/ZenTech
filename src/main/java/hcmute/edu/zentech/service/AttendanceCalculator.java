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
        // 1. Check Schedule Adjustments (highest direct priority from manager)
        List<ScheduleAdjustment> adjustments = scheduleAdjustmentRepository
                .findByEmployeeIdAndWorkDateBetween(employeeId, date, date);
        for (ScheduleAdjustment sa : adjustments) {
            if (sa.getStatus() == ApprovalStatus.APPROVED) {
                return sa.getAdjustedShift();
            }
        }

        // 2. Check Approved Shift Swaps / Covers
        List<ShiftSwapRequest> swapRequests = shiftSwapRequestRepository
                .findApprovedSwapsForEmployeeInRange(employeeId, date, date, ApprovalStatus.APPROVED);
        for (ShiftSwapRequest req : swapRequests) {
            if (req.getType() == SwapRequestType.COVER) {
                if (req.getTargetEmployee().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                    // This employee covered someone else's shift
                    return req.getShift();
                } else if (req.getRequester().getId().equals(employeeId) && req.getWorkDate().equals(date)) {
                    // This employee was covered by someone else -> they are OFF
                    return null;
                }
            } else if (req.getType() == SwapRequestType.SWAP) {
                if (req.getWorkDate().equals(req.getTargetWorkDate())) {
                    // Same day swap
                    if (req.getRequester().getId().equals(employeeId)) {
                        return req.getTargetShift();
                    } else if (req.getTargetEmployee().getId().equals(employeeId)) {
                        return req.getShift();
                    }
                } else {
                    // Different days swap
                    if (req.getWorkDate().equals(date)) {
                        if (req.getRequester().getId().equals(employeeId)) {
                            return null;
                        } else if (req.getTargetEmployee().getId().equals(employeeId)) {
                            return req.getShift();
                        }
                    } else if (req.getTargetWorkDate() != null && req.getTargetWorkDate().equals(date)) {
                        if (req.getRequester().getId().equals(employeeId)) {
                            return req.getTargetShift();
                        } else if (req.getTargetEmployee().getId().equals(employeeId)) {
                            return null;
                        }
                    }
                }
            }
        }

        // 3. Fallback to default/base shift from EmployeeShift
        return employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, date)
                .map(EmployeeShift::getShift)
                .orElse(null);
    }

    public AttendanceRecordResponse calculateDayAttendance(Employee employee, LocalDate date) {
        Shift shift = resolveEffectiveShift(employee.getId(), date);
        String shiftName = (shift != null) ? shift.getName() : "Nghỉ";

        // Get raw events
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<AttendanceEvent> rawEvents = attendanceEventRepository
                .findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(employee.getId(), startOfDay, endOfDay);

        // Get approved attendance adjustments
        List<AttendanceAdjustment> adjustments = attendanceAdjustmentRepository
                .findByEmployeeIdAndWorkDateBetweenAndStatus(employee.getId(), date, date, ApprovalStatus.APPROVED);

        // Get all timestamps from raw events
        List<LocalDateTime> times = new ArrayList<>();
        for (AttendanceEvent event : rawEvents) {
            times.add(event.getTimestamp());
        }

        // Get all timestamps from approved adjustments
        for (AttendanceAdjustment adj : adjustments) {
            times.add(LocalDateTime.of(date, adj.getProposedTime()));
        }

        // Sort and de-duplicate (remove events within 10 seconds of each other)
        Collections.sort(times);
        List<LocalDateTime> uniqueTimes = new ArrayList<>();
        for (LocalDateTime t : times) {
            if (uniqueTimes.isEmpty()) {
                uniqueTimes.add(t);
            } else {
                LocalDateTime lastAdded = uniqueTimes.get(uniqueTimes.size() - 1);
                if (Duration.between(lastAdded, t).abs().toSeconds() > 10) {
                    uniqueTimes.add(t);
                }
            }
        }

        double totalHours = 0.0;
        LocalDateTime firstCheckIn = null;
        LocalDateTime lastCheckOut = null;

        // Pair sequentially: odd index are Check-in, even index are Check-out
        for (int i = 0; i < uniqueTimes.size(); i += 2) {
            LocalDateTime in = uniqueTimes.get(i);
            if (firstCheckIn == null) firstCheckIn = in;

            if (i + 1 < uniqueTimes.size()) {
                LocalDateTime out = uniqueTimes.get(i + 1);
                lastCheckOut = out;
                Duration duration = Duration.between(in, out);
                totalHours += Math.max(0.0, duration.toMinutes() / 60.0);
            }
        }

        long lateMinutes = 0;
        long earlyMinutes = 0;
        String status = "OFF";

        if (shift == null || shift.getType() == ShiftType.OFF) {
            status = "OFF";
        } else {
            LocalTime shiftStart = shift.getStartTime();
            LocalTime shiftEnd = shift.getEndTime();

            if (uniqueTimes.isEmpty()) {
                List<LeaveRequest> leaves = leaveRequestRepository
                        .findApprovedLeavesForEmployeeInRange(employee.getId(), date, date, ApprovalStatus.APPROVED);
                if (!leaves.isEmpty()) {
                    status = "ABSENT_EXCUSED";
                } else {
                    status = "ABSENT_UNEXCUSED";
                }
            } else if (uniqueTimes.size() % 2 != 0) {
                // Odd number of events means they checked in but missed check-out (or vice versa)
                // Let's assume they checked in (first event) but missing check-out
                status = "MISSING_CHECK_OUT";
                if (shiftStart != null && firstCheckIn != null) {
                    LocalTime inTime = firstCheckIn.toLocalTime();
                    int grace = (shift.getGracePeriodMinutes() != null) ? shift.getGracePeriodMinutes() : 15;
                    if (inTime.isAfter(shiftStart.plusMinutes(grace))) {
                        lateMinutes = Duration.between(shiftStart, inTime).toMinutes();
                    }
                }
            } else {
                LocalTime inTime = firstCheckIn.toLocalTime();
                LocalTime outTime = lastCheckOut.toLocalTime();


                int grace = (shift.getGracePeriodMinutes() != null) ? shift.getGracePeriodMinutes() : 15;

                boolean isLate = shiftStart != null && inTime.isAfter(shiftStart.plusMinutes(grace));
                boolean isEarly = shiftEnd != null && outTime.isBefore(shiftEnd);

                if (isLate) {
                    lateMinutes = Duration.between(shiftStart, inTime).toMinutes();
                }
                if (isEarly) {
                    earlyMinutes = Duration.between(outTime, shiftEnd).toMinutes();
                }

                if (isLate && isEarly) {
                    status = "LATE_AND_EARLY";
                } else if (isLate) {
                    status = "LATE";
                } else if (isEarly) {
                    status = "EARLY_CHECKOUT";
                } else {
                    status = "ON_TIME";
                }
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
                .detailTimes(uniqueTimes)
                .build();
    }

    private static class TimeEvent {
        private final LocalDateTime time;
        private final boolean isCheckIn;

        public TimeEvent(LocalDateTime time, boolean isCheckIn) {
            this.time = time;
            this.isCheckIn = isCheckIn;
        }

        public LocalDateTime getTime() { return time; }
        public boolean isCheckIn() { return isCheckIn; }
    }
}

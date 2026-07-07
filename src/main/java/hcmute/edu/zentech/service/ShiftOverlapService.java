package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.model.ShiftType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ShiftOverlapService {
    private static final int DEFAULT_EARLY_CHECK_IN_MINUTES = 30;
    private static final int DEFAULT_LATE_CHECK_OUT_MINUTES = 60;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public boolean hasCaptureWindow(Shift shift) {
        return shift != null
                && shift.getType() != ShiftType.OFF
                && shift.getStartTime() != null
                && shift.getEndTime() != null;
    }

    public CaptureWindow captureWindow(LocalDate workDate, Shift shift) {
        LocalDateTime start = LocalDateTime.of(workDate, shift.getStartTime())
                .minusMinutes(defaultInt(shift.getEarlyCheckInMinutes(), DEFAULT_EARLY_CHECK_IN_MINUTES));
        LocalDateTime end = LocalDateTime.of(workDate, shift.getEndTime());
        if (!shift.getEndTime().isAfter(shift.getStartTime())) {
            end = end.plusDays(1);
        }
        end = end.plusMinutes(defaultInt(shift.getLateCheckOutMinutes(), DEFAULT_LATE_CHECK_OUT_MINUTES));
        return new CaptureWindow(start, end);
    }

    public boolean overlapsInclusive(CaptureWindow first, CaptureWindow second) {
        return !first.end().isBefore(second.start()) && !first.start().isAfter(second.end());
    }

    public String format(CaptureWindow window) {
        if (window.start().toLocalDate().equals(window.end().toLocalDate())) {
            return window.start().format(TIME_FORMATTER) + "-" + window.end().format(TIME_FORMATTER);
        }
        return window.start().format(DATE_TIME_FORMATTER) + " - " + window.end().format(DATE_TIME_FORMATTER);
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }

    public record CaptureWindow(LocalDateTime start, LocalDateTime end) {
    }
}

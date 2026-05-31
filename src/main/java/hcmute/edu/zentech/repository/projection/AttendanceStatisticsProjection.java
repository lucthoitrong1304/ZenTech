package hcmute.edu.zentech.repository.projection;

public interface AttendanceStatisticsProjection {
    long getTotalRecords();
    long getTotalOnTime();
    long getTotalLate();
    long getTotalEarly();
}

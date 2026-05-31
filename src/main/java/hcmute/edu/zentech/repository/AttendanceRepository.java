package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Attendance;
import hcmute.edu.zentech.repository.projection.AttendanceRecordProjection;
import hcmute.edu.zentech.repository.projection.AttendanceStatisticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    @Query("SELECT a.id as id, a.employee.id as employeeId, a.employee.fullName as employeeName, " +
           "a.checkInTime as checkInTime, a.status as status " +
           "FROM Attendance a " +
           "WHERE a.checkInTime >= :startDate AND a.checkInTime <= :endDate " +
           "ORDER BY a.checkInTime DESC")
    Page<AttendanceRecordProjection> findAllRecordsBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                                                @Param("endDate") LocalDateTime endDate, 
                                                                Pageable pageable);

    @Query("SELECT a.id as id, a.employee.id as employeeId, a.employee.fullName as employeeName, " +
           "a.checkInTime as checkInTime, a.status as status " +
           "FROM Attendance a " +
           "WHERE a.employee.id = :employeeId AND a.checkInTime >= :startDate AND a.checkInTime <= :endDate " +
           "ORDER BY a.checkInTime DESC")
    Page<AttendanceRecordProjection> findRecordsByEmployeeIdAndDates(@Param("employeeId") UUID employeeId,
                                                                     @Param("startDate") LocalDateTime startDate, 
                                                                     @Param("endDate") LocalDateTime endDate, 
                                                                     Pageable pageable);

    @Query("SELECT COUNT(a.id) as totalRecords, " +
           "COALESCE(SUM(CASE WHEN a.status = hcmute.edu.zentech.model.AttendanceStatus.ON_TIME THEN 1 ELSE 0 END), 0L) as totalOnTime, " +
           "COALESCE(SUM(CASE WHEN a.status = hcmute.edu.zentech.model.AttendanceStatus.LATE THEN 1 ELSE 0 END), 0L) as totalLate, " +
           "COALESCE(SUM(CASE WHEN a.status = hcmute.edu.zentech.model.AttendanceStatus.EARLY THEN 1 ELSE 0 END), 0L) as totalEarly " +
           "FROM Attendance a " +
           "WHERE a.checkInTime >= :startDate AND a.checkInTime <= :endDate")
    AttendanceStatisticsProjection getStatisticsBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                                             @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(a.id) as totalRecords, " +
           "COALESCE(SUM(CASE WHEN a.status = hcmute.edu.zentech.model.AttendanceStatus.ON_TIME THEN 1 ELSE 0 END), 0L) as totalOnTime, " +
           "COALESCE(SUM(CASE WHEN a.status = hcmute.edu.zentech.model.AttendanceStatus.LATE THEN 1 ELSE 0 END), 0L) as totalLate, " +
           "COALESCE(SUM(CASE WHEN a.status = hcmute.edu.zentech.model.AttendanceStatus.EARLY THEN 1 ELSE 0 END), 0L) as totalEarly " +
           "FROM Attendance a " +
           "WHERE a.employee.id = :employeeId AND a.checkInTime >= :startDate AND a.checkInTime <= :endDate")
    AttendanceStatisticsProjection getStatisticsByEmployeeIdAndDates(@Param("employeeId") UUID employeeId,
                                                                     @Param("startDate") LocalDateTime startDate, 
                                                                     @Param("endDate") LocalDateTime endDate);
}

package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.EmployeeShift;
import hcmute.edu.zentech.repository.projection.EmployeeWeeklyScheduleProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, UUID> {

    @Query("SELECT es.employee.id AS employeeId, es.id AS employeeShiftId, " +
           "s.id AS shiftId, s.name AS shiftName, s.colorCode AS colorCode, " +
           "s.startTime AS startTime, s.endTime AS endTime, s.type AS shiftType, " +
           "s.earlyCheckInMinutes AS earlyCheckInMinutes, s.lateCheckOutMinutes AS lateCheckOutMinutes, " +
           "s.onTimeCheckInStartMinutes AS onTimeCheckInStartMinutes, s.onTimeCheckInEndMinutes AS onTimeCheckInEndMinutes, " +
           "s.onTimeCheckOutStartMinutes AS onTimeCheckOutStartMinutes, s.onTimeCheckOutEndMinutes AS onTimeCheckOutEndMinutes, " +
           "es.workDate AS workDate " +
           "FROM EmployeeShift es " +
           "JOIN es.shift s " +
           "WHERE es.employee.id IN :employeeIds AND es.workDate BETWEEN :startDate AND :endDate")
    List<EmployeeWeeklyScheduleProjection> findProjectionsByEmployeeIdsAndDateRange(
            @Param("employeeIds") List<UUID> employeeIds, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
            
    @Query("SELECT es FROM EmployeeShift es JOIN FETCH es.shift WHERE es.employee.id = :employeeId AND es.workDate = :workDate ORDER BY es.shift.startTime ASC")
    List<EmployeeShift> findByEmployeeIdAndWorkDate(
            @Param("employeeId") UUID employeeId,
            @Param("workDate") LocalDate workDate);

    @Query("SELECT es FROM EmployeeShift es JOIN FETCH es.shift WHERE es.id = :id")
    Optional<EmployeeShift> findByIdWithShift(@Param("id") UUID id);

    void deleteByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
    
    void deleteByEmployeeIdInAndWorkDateBetween(List<UUID> employeeIds, LocalDate startDate, LocalDate endDate);
}


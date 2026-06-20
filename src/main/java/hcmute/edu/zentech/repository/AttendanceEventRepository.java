package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AttendanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {

    List<AttendanceEvent> findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(
            UUID employeeId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT e FROM AttendanceEvent e WHERE e.employee.id IN :employeeIds AND e.timestamp >= :start AND e.timestamp <= :end ORDER BY e.timestamp ASC")
    List<AttendanceEvent> findEventsForEmployeesInRange(
            @Param("employeeIds") List<UUID> employeeIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}

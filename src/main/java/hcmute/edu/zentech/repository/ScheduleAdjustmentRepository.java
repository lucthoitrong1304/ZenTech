package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ScheduleAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleAdjustmentRepository extends JpaRepository<ScheduleAdjustment, UUID> {

    List<ScheduleAdjustment> findByEmployeeIdAndWorkDateBetween(UUID employeeId, LocalDate start, LocalDate end);

    List<ScheduleAdjustment> findByEmployeeIdInAndWorkDateBetween(List<UUID> employeeIds, LocalDate start, LocalDate end);
}

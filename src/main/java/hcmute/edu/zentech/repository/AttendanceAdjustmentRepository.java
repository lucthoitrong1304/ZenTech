package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.AttendanceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, UUID> {

    List<AttendanceAdjustment> findByEmployeeIdAndWorkDateBetweenAndStatus(
            UUID employeeId, LocalDate start, LocalDate end, ApprovalStatus status);

    List<AttendanceAdjustment> findByEmployeeIdInAndWorkDateBetweenAndStatus(
            List<UUID> employeeIds, LocalDate start, LocalDate end, ApprovalStatus status);

    List<AttendanceAdjustment> findByStatus(ApprovalStatus status);

    List<AttendanceAdjustment> findByEmployeeIdOrderByRequestedAtDesc(UUID employeeId);
}

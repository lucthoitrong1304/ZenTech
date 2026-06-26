package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.EmployeeLeaveQuota;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeLeaveQuotaRepository extends JpaRepository<EmployeeLeaveQuota, UUID> {
    @EntityGraph(attributePaths = {"leaveType"})
    List<EmployeeLeaveQuota> findByEmployeeIdAndYear(UUID employeeId, int year);

    Optional<EmployeeLeaveQuota> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, UUID leaveTypeId, int year);
}

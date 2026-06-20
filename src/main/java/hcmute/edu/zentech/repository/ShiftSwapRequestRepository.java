package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.ShiftSwapRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequest, UUID> {

    @Query("SELECT r FROM ShiftSwapRequest r WHERE r.status = :status AND " +
           "((r.requester.id = :empId AND r.workDate BETWEEN :start AND :end) OR " +
           "(r.targetEmployee.id = :empId AND r.workDate BETWEEN :start AND :end) OR " +
           "(r.targetEmployee.id = :empId AND r.targetWorkDate BETWEEN :start AND :end))")
    List<ShiftSwapRequest> findApprovedSwapsForEmployeeInRange(
            @Param("empId") UUID employeeId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") ApprovalStatus status);

    @Query("SELECT r FROM ShiftSwapRequest r WHERE r.status = :status AND " +
           "((r.requester.id IN :empIds AND r.workDate BETWEEN :start AND :end) OR " +
           "(r.targetEmployee.id IN :empIds AND r.workDate BETWEEN :start AND :end) OR " +
           "(r.targetEmployee.id IN :empIds AND r.targetWorkDate BETWEEN :start AND :end))")
    List<ShiftSwapRequest> findApprovedSwapsForEmployeesInRange(
            @Param("empIds") List<UUID> employeeIds,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") ApprovalStatus status);

    List<ShiftSwapRequest> findByStatus(ApprovalStatus status);

    @Query("SELECT r FROM ShiftSwapRequest r WHERE r.requester.id = :empId OR r.targetEmployee.id = :empId ORDER BY r.requestedAt DESC")
    List<ShiftSwapRequest> findMySwaps(@Param("empId") UUID employeeId);
}

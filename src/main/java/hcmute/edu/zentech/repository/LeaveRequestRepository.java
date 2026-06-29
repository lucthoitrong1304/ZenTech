package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.LeaveRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id = :empId AND l.status = :status AND " +
           "l.startDate <= :end AND l.endDate >= :start")
    List<LeaveRequest> findApprovedLeavesForEmployeeInRange(
            @Param("empId") UUID employeeId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") ApprovalStatus status);

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id IN :empIds AND l.status = :status AND " +
           "l.startDate <= :end AND l.endDate >= :start")
    List<LeaveRequest> findApprovedLeavesForEmployeesInRange(
            @Param("empIds") List<UUID> employeeIds,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") ApprovalStatus status);

    @EntityGraph(attributePaths = {"employee"})
    List<LeaveRequest> findByStatus(ApprovalStatus status);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    List<LeaveRequest> findByStatusIn(List<ApprovalStatus> statuses);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    List<LeaveRequest> findByEmployeeIdOrderByRequestedAtDesc(UUID employeeId);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    List<LeaveRequest> findByEmployeeIdAndLeaveTypeIdAndStatusInAndStartDateBetween(
            UUID employeeId,
            UUID leaveTypeId,
            List<ApprovalStatus> statuses,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("SELECT l FROM LeaveRequest l JOIN l.leaveType lt WHERE l.employee.id = :empId " +
           "AND lt.code = 'WFH' AND l.status IN :statuses AND l.startDate <= :date AND l.endDate >= :date")
    List<LeaveRequest> findWfhRequestsForEmployeeOnDate(
            @Param("empId") UUID employeeId,
            @Param("date") LocalDate date,
            @Param("statuses") List<ApprovalStatus> statuses);

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id = :empId AND l.status IN :statuses AND " +
           "l.startDate <= :end AND l.endDate >= :start")
    List<LeaveRequest> findLeavesForEmployeeInRangeWithStatuses(
            @Param("empId") UUID employeeId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("statuses") List<ApprovalStatus> statuses);
}

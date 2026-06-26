package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {
    Optional<LeaveType> findByCode(String code);

    boolean existsByCode(String code);

    List<LeaveType> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<LeaveType> findAllByOrderBySortOrderAscNameAsc();
}

package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AttendanceLocationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttendanceLocationPolicyRepository extends JpaRepository<AttendanceLocationPolicy, UUID> {
    Optional<AttendanceLocationPolicy> findTopByOrderByUpdatedAtDesc();
}

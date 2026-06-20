package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.PayPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayPeriodRepository extends JpaRepository<PayPeriod, UUID> {
    
    @Query("SELECT p FROM PayPeriod p WHERE p.startDate <= :date AND p.endDate >= :date")
    Optional<PayPeriod> findPeriodActiveAt(@Param("date") LocalDate date);
}

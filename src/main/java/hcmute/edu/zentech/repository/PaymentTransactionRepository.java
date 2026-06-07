package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.PaymentGateway;
import hcmute.edu.zentech.model.PaymentTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    @EntityGraph(attributePaths = {"order"})
    Optional<PaymentTransaction> findByGatewayAndRequestId(PaymentGateway gateway, String requestId);
}

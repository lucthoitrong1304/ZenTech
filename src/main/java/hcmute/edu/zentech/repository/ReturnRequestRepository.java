package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ReturnRequest;
import hcmute.edu.zentech.model.ReturnRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {
    
    @Query("SELECT r FROM ReturnRequest r JOIN FETCH r.order o JOIN FETCH o.customer c WHERE r.id = :id")
    Optional<ReturnRequest> findDetailById(UUID id);
    
    @Query("SELECT r FROM ReturnRequest r JOIN FETCH r.order o JOIN FETCH o.customer c ORDER BY r.createdAt DESC")
    List<ReturnRequest> findAllWithDetails();

    @EntityGraph(attributePaths = {"order"})
    List<ReturnRequest> findByOrder_Customer_IdOrderByCreatedAtDesc(UUID customerId);

    boolean existsByOrder_IdAndStatus(UUID orderId, ReturnRequestStatus status);
}

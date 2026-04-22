package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.repository.projection.CustomerOrderAggregateProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.customer.id = :customerId
            """)
    Page<Order> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    @Query("""
            SELECT o.customer.id AS customerId,
                   COUNT(o.id) AS totalOrders,
                   COALESCE(SUM(o.finalPrice), 0) AS totalSpent,
                   MAX(o.createdAt) AS lastOrderAt
            FROM Order o
            WHERE o.customer.id IN :customerIds
              AND o.orderStatus <> :cancelledStatus
              AND o.paymentStatus <> :refundedStatus
              AND (o.orderStatus = :completedStatus OR o.paymentStatus = :successStatus)
            GROUP BY o.customer.id
            """)
    List<CustomerOrderAggregateProjection> findCustomerOrderAggregates(
            @Param("customerIds") List<UUID> customerIds,
            @Param("cancelledStatus") OrderStatus cancelledStatus,
            @Param("completedStatus") OrderStatus completedStatus,
            @Param("refundedStatus") PaymentStatus refundedStatus,
            @Param("successStatus") PaymentStatus successStatus
    );
}

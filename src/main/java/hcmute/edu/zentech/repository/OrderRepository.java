package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.repository.projection.CustomerOrderAggregateProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.repository.projection.CustomerOrderAggregateProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
            SELECT o
            FROM Order o
            WHERE o.customer.id = :customerId
              AND (:status IS NULL OR o.orderStatus = :status)
            """)
    Page<Order> findByCustomerIdAndOptionalStatus(
            @Param("customerId") UUID customerId,
            @Param("status") OrderStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"address", "orderCoupons"})
    Optional<Order> findByIdAndCustomer_Id(UUID orderId, UUID customerId);

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

    @EntityGraph(attributePaths = {"customer", "customer.userInfo", "address"})
    @Query(
            value = """
                    SELECT o
                    FROM Order o
                    JOIN o.customer c
                    JOIN c.userInfo u
                    WHERE (:keyword IS NULL
                        OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(CAST(o.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:orderStatus IS NULL OR o.orderStatus = :orderStatus)
                      AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                      AND (:startDate IS NULL OR o.createdAt >= :startDate)
                      AND (:endDate IS NULL OR o.createdAt <= :endDate)
                    """,
            countQuery = """
                    SELECT COUNT(o)
                    FROM Order o
                    JOIN o.customer c
                    JOIN c.userInfo u
                    WHERE (:keyword IS NULL
                        OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(CAST(o.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:orderStatus IS NULL OR o.orderStatus = :orderStatus)
                      AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
                      AND (:startDate IS NULL OR o.createdAt >= :startDate)
                      AND (:endDate IS NULL OR o.createdAt <= :endDate)
                    """
    )
    Page<Order> searchManagementOrders(
            @Param("keyword") String keyword,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"customer", "customer.userInfo", "address", "orderCoupons"})
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findManagementDetailById(@Param("orderId") UUID orderId);
}

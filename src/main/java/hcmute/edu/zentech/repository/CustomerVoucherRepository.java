package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.CustomerVoucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface CustomerVoucherRepository extends JpaRepository<CustomerVoucher, UUID> {
    @EntityGraph(attributePaths = {"coupon"})
    Page<CustomerVoucher> findByCustomer_Id(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"coupon"})
    Page<CustomerVoucher> findByCustomer_IdAndUsedAtIsNotNull(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"coupon"})
    @Query(
            value = """
                    SELECT cv
                    FROM CustomerVoucher cv
                    JOIN cv.coupon c
                    WHERE cv.customer.id = :customerId
                      AND cv.usedAt IS NULL
                      AND (c.active = false OR (c.endAt IS NOT NULL AND c.endAt < :now))
                    """,
            countQuery = """
                    SELECT COUNT(cv)
                    FROM CustomerVoucher cv
                    JOIN cv.coupon c
                    WHERE cv.customer.id = :customerId
                      AND cv.usedAt IS NULL
                      AND (c.active = false OR (c.endAt IS NOT NULL AND c.endAt < :now))
                    """
    )
    Page<CustomerVoucher> findExpiredByCustomerId(
            @Param("customerId") UUID customerId,
            @Param("now") Instant now,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"coupon"})
    @Query(
            value = """
                    SELECT cv
                    FROM CustomerVoucher cv
                    JOIN cv.coupon c
                    WHERE cv.customer.id = :customerId
                      AND cv.usedAt IS NULL
                      AND c.active = true
                      AND (c.startAt IS NULL OR c.startAt <= :now)
                      AND (c.endAt IS NULL OR c.endAt >= :now)
                    """,
            countQuery = """
                    SELECT COUNT(cv)
                    FROM CustomerVoucher cv
                    JOIN cv.coupon c
                    WHERE cv.customer.id = :customerId
                      AND cv.usedAt IS NULL
                      AND c.active = true
                      AND (c.startAt IS NULL OR c.startAt <= :now)
                      AND (c.endAt IS NULL OR c.endAt >= :now)
                    """
    )
    Page<CustomerVoucher> findAvailableByCustomerId(
            @Param("customerId") UUID customerId,
            @Param("now") Instant now,
            Pageable pageable
    );
}

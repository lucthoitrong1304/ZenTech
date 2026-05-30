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

    @EntityGraph(attributePaths = {"coupon", "customer", "customer.userInfo"})
    @Query(
            value = """
                    SELECT cv
                    FROM CustomerVoucher cv
                    JOIN cv.customer cust
                    JOIN cv.coupon coup
                    WHERE (:keyword IS NULL OR LOWER(cust.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(cust.userInfo.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:couponCode IS NULL OR LOWER(coup.code) LIKE LOWER(CONCAT('%', :couponCode, '%')))
                      AND (:status IS NULL 
                           OR (:status = 'USED' AND cv.usedAt IS NOT NULL)
                           OR (:status = 'EXPIRED' AND cv.usedAt IS NULL AND coup.endAt IS NOT NULL AND coup.endAt < :now)
                           OR (:status = 'AVAILABLE' AND cv.usedAt IS NULL AND (coup.endAt IS NULL OR coup.endAt >= :now))
                          )
                    """,
            countQuery = """
                    SELECT COUNT(cv)
                    FROM CustomerVoucher cv
                    JOIN cv.customer cust
                    JOIN cv.coupon coup
                    WHERE (:keyword IS NULL OR LOWER(cust.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(cust.userInfo.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:couponCode IS NULL OR LOWER(coup.code) LIKE LOWER(CONCAT('%', :couponCode, '%')))
                      AND (:status IS NULL 
                           OR (:status = 'USED' AND cv.usedAt IS NOT NULL)
                           OR (:status = 'EXPIRED' AND cv.usedAt IS NULL AND coup.endAt IS NOT NULL AND coup.endAt < :now)
                           OR (:status = 'AVAILABLE' AND cv.usedAt IS NULL AND (coup.endAt IS NULL OR coup.endAt >= :now))
                          )
                    """
    )
    Page<CustomerVoucher> searchCustomerVouchers(
            @Param("keyword") String keyword,
            @Param("couponCode") String couponCode,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("SELECT COUNT(cv) > 0 FROM CustomerVoucher cv WHERE cv.coupon.id = :couponId AND cv.usedAt IS NOT NULL")
    boolean existsUsedVoucherByCouponId(@Param("couponId") UUID couponId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM CustomerVoucher cv WHERE cv.coupon.id = :couponId")
    void deleteVouchersByCouponId(@Param("couponId") UUID couponId);
}

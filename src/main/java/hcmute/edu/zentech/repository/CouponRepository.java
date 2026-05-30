package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.CouponType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    
    boolean existsByCode(String code);

    @Query("""
            select count(c) > 0
            from Coupon c
            where lower(c.code) = lower(:code)
            and (:couponId is null or c.id <> :couponId)
            """)
    boolean existsCodeExcludingId(@Param("code") String code, @Param("couponId") UUID couponId);

    @Query(
            value = """
                    select c
                    from Coupon c
                    where (:keyword is null or lower(c.code) like lower(concat('%', :keyword, '%')))
                    and (:type is null or c.type = :type)
                    and (:active is null or c.active = :active)
                    """,
            countQuery = """
                    select count(c)
                    from Coupon c
                    where (:keyword is null or lower(c.code) like lower(concat('%', :keyword, '%')))
                    and (:type is null or c.type = :type)
                    and (:active is null or c.active = :active)
                    """
    )
    Page<Coupon> searchCoupons(
            @Param("keyword") String keyword,
            @Param("type") CouponType type,
            @Param("active") Boolean active,
            Pageable pageable
    );

    Optional<Coupon> findByCodeIgnoreCase(String code);
}

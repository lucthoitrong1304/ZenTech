package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.OrderCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderCouponRepository extends JpaRepository<OrderCoupon, UUID> {

    @Query("SELECT COALESCE(SUM(oc.appliedAmount), 0.0) FROM OrderCoupon oc")
    Double sumAllAppliedAmount();
}

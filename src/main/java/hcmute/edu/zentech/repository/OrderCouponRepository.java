package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.OrderCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderCouponRepository extends JpaRepository<OrderCoupon, UUID> {
}

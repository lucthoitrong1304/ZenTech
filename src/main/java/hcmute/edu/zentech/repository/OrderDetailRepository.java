package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.OrderDetail;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, UUID> {
    @EntityGraph(attributePaths = {"order", "productVariant", "productVariant.product"})
    List<OrderDetail> findByOrder_IdIn(Collection<UUID> orderIds);
}

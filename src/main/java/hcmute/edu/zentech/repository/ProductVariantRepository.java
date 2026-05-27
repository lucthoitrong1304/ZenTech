package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    @Query("""
            select v
            from ProductVariant v
            join fetch v.product p
            where v.id = :variantId
            and v.deleted = false
            and p.deleted = false
            """)
    Optional<ProductVariant> findOrderableById(@Param("variantId") UUID variantId);
}

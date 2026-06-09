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

    @Query(
            value = """
                    select v
                    from ProductVariant v
                    join fetch v.product p
                    where v.deleted = false
                    and p.deleted = false
                    and (:keyword is null
                         or lower(p.productName) like lower(concat('%', :keyword, '%'))
                         or lower(v.name) like lower(concat('%', :keyword, '%')))
                    and (:stockStatus = 'all'
                         or (:stockStatus = 'out_of_stock' and v.stockQuantity <= 0)
                         or (:stockStatus = 'low_stock' and v.stockQuantity > 0 and v.stockQuantity < 10)
                         or (:stockStatus = 'in_stock' and v.stockQuantity >= 10))
                    """,
            countQuery = """
                    select count(v)
                    from ProductVariant v
                    join v.product p
                    where v.deleted = false
                    and p.deleted = false
                    and (:keyword is null
                         or lower(p.productName) like lower(concat('%', :keyword, '%'))
                         or lower(v.name) like lower(concat('%', :keyword, '%')))
                    and (:stockStatus = 'all'
                         or (:stockStatus = 'out_of_stock' and v.stockQuantity <= 0)
                         or (:stockStatus = 'low_stock' and v.stockQuantity > 0 and v.stockQuantity < 10)
                         or (:stockStatus = 'in_stock' and v.stockQuantity >= 10))
                    """
    )
    org.springframework.data.domain.Page<ProductVariant> searchInventory(
            @Param("keyword") String keyword,
            @Param("stockStatus") String stockStatus,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            select count(v)
            from ProductVariant v
            join v.product p
            where v.deleted = false
            and p.deleted = false
            """)
    long countActiveVariants();

    @Query("""
            select count(v)
            from ProductVariant v
            join v.product p
            where v.deleted = false
            and p.deleted = false
            and v.stockQuantity <= 0
            """)
    long countOutOfStockVariants();

    @Query("""
            select count(v)
            from ProductVariant v
            join v.product p
            where v.deleted = false
            and p.deleted = false
            and v.stockQuantity > 0
            and v.stockQuantity < 10
            """)
    long countLowStockVariants();

    @Query("""
            select v
            from ProductVariant v
            join fetch v.product p
            where v.deleted = false
            and p.deleted = false
            and v.stockQuantity < 10
            """)
    java.util.List<ProductVariant> findLowStockAndOutOfStockVariants();
}



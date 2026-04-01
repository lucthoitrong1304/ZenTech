package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    ProductCategory findCategoryByShortName(String shortName);

    @Query("""
            select distinct c
            from ProductCategory c
            left join fetch c.productList p
            left join fetch p.imageList
            left join fetch p.reviewList
            left join fetch p.variants
            where c.id = :categoryId
            """)
    Optional<ProductCategory> findCategoryWithProductsById(@Param("categoryId") UUID categoryId);
}

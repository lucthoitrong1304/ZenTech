package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query("""
            select distinct p
            from Product p
            left join fetch p.categories
            left join fetch p.variants
            left join fetch p.productGroup
            where p.id = :productId
            """)
    Optional<Product> findProductDetailById(@Param("productId") UUID productId);

    @Query("""
            select distinct p
            from Product p
            join fetch p.categories c
            left join fetch p.reviewList
            left join fetch p.variants
            where c.id in :categoryIds
            """)
    List<Product> findProductsForSimilarityByCategoryIds(@Param("categoryIds") Set<UUID> categoryIds);

    @Query("""
            select distinct p
            from Product p
            left join fetch p.imageKeys
            where p.productGroup.id = :groupId
            and p.id <> :productId
            """)
    List<Product> findGroupProducts(@Param("groupId") UUID groupId, @Param("productId") UUID productId);

    boolean existsByProductName(String productName);
}

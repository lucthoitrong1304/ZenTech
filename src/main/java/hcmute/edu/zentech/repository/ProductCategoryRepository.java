package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    ProductCategory findCategoryByShortName(String shortName);

    boolean existsByParent_Id(UUID parentId);

    @Query("""
            select count(p)
            from Product p
            join p.categories c
            where c.id = :categoryId
              and p.deleted = false
            """)
    long countActiveProductsByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("""
            select c
            from ProductCategory c
            left join fetch c.parent
            order by c.priority asc, c.categoryName asc, c.id asc
            """)
    List<ProductCategory> findAllWithParent();

    @Query("""
            select distinct c
            from ProductCategory c
            left join fetch c.productList p
            left join fetch p.reviewList
            left join fetch p.variants
            where c.id = :categoryId
            """)
    Optional<ProductCategory> findCategoryWithProductsById(@Param("categoryId") UUID categoryId);

    @Query("""
            select distinct c
            from ProductCategory c
            left join fetch c.productList p
            left join fetch p.reviewList
            left join fetch p.variants
            where c.id in :categoryIds
            """)
    List<ProductCategory> findCategoriesWithProductsByIds(@Param("categoryIds") java.util.Collection<UUID> categoryIds);
}


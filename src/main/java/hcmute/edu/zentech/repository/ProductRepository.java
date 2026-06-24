package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            and p.deleted = false
            """)
    Optional<Product> findProductDetailById(@Param("productId") UUID productId);

    @Query("""
            select distinct p
            from Product p
            left join fetch p.categories
            left join fetch p.variants
            left join fetch p.productGroup
            left join fetch p.imageKeys
            where p.id = :productId
            """)
    Optional<Product> findManagementDetailById(@Param("productId") UUID productId);

    @Query(
            value = """
                    select distinct p
                    from Product p
                    left join p.productGroup pg
                    where (:includeDeleted = true or p.deleted = false)
                    and (:keyword is null
                         or lower(p.productName) like lower(concat('%', :keyword, '%'))
                         or lower(pg.groupName) like lower(concat('%', :keyword, '%')))
                    """,
            countQuery = """
                    select count(distinct p)
                    from Product p
                    left join p.productGroup pg
                    where (:includeDeleted = true or p.deleted = false)
                    and (:keyword is null
                         or lower(p.productName) like lower(concat('%', :keyword, '%'))
                         or lower(pg.groupName) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<Product> searchManagementProducts(
            @Param("keyword") String keyword,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable);

    @Query("""
            select distinct p
            from Product p
            left join fetch p.variants v
            left join fetch p.reviewList r
            left join p.productGroup pg
            where p.deleted = false
            and (:keyword is null
                 or lower(p.productName) like lower(concat('%', :keyword, '%'))
                 or lower(pg.groupName) like lower(concat('%', :keyword, '%')))
            """)
    List<Product> searchActiveProductsWithVariantsAndReviews(@Param("keyword") String keyword);

    @Query("""
            select distinct p
            from Product p
            join fetch p.categories c
            left join fetch p.reviewList
            left join fetch p.variants
            where c.id in :categoryIds
            and p.deleted = false
            """)
    List<Product> findProductsForSimilarityByCategoryIds(@Param("categoryIds") Set<UUID> categoryIds);

    @Query("""
            select distinct p
            from Product p
            left join fetch p.imageKeys
            where p.productGroup.id = :groupId
            and p.id <> :productId
            and p.deleted = false
            """)
    List<Product> findGroupProducts(@Param("groupId") UUID groupId, @Param("productId") UUID productId);

    boolean existsByProductName(String productName);

    boolean existsByProductNameAndDeletedFalse(String productName);

    Optional<Product> findFirstByProductNameIgnoreCaseAndDeletedFalse(String productName);

    boolean existsByIdAndDeletedFalse(UUID productId);

    @Query("""
            select count(p) > 0
            from Product p
            where lower(p.productName) = lower(:productName)
            and p.deleted = false
            and (:productId is null or p.id <> :productId)
            """)
    boolean existsActiveProductNameExcludingId(
            @Param("productName") String productName,
            @Param("productId") UUID productId);
}

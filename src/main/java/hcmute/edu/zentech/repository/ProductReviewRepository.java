package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {
    @EntityGraph(attributePaths = "customer")
    Page<ProductReview> findByProduct_Id(UUID productId, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Optional<ProductReview> findByIdAndProduct_Id(UUID reviewId, UUID productId);

    long countByProduct_Id(UUID productId);

    @Query("select avg(pr.rating) from ProductReview pr where pr.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") UUID productId);
}

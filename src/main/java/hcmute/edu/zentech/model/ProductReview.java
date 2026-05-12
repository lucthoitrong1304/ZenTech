package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_reviews")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProductReview {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id")
    private UUID id;

    private Integer rating;

    private String comment;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "product_review_images",
            joinColumns = @JoinColumn(name = "review_id")
    )
    @Column(name = "image_key", length = 1000)
    @OrderColumn(name = "image_order")
    private List<String> imageKeys = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
}
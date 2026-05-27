package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_id")
    private UUID id;

    @Column(nullable = false)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Column(columnDefinition = "TEXT")
    private String compatibility;

    @Column(columnDefinition = "TEXT")
    private String boxContents;

    @Column(columnDefinition = "TEXT")
    private String supportInfo;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    private Instant deletedAt;

    @Column(name = "representative_image_key", length = 1000)
    private String representativeImageKey;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "product_images",
            joinColumns = @JoinColumn(name = "product_id")
    )
    @Column(name = "image_key", length = 1000)
    @OrderColumn(name = "image_order")
    private List<String> imageKeys = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_group_id")
    private ProductGroup productGroup;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductReview> reviewList = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductVariant> variants = new HashSet<>();

    @Singular("category")
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_category_mapping",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<ProductCategory> categories = new HashSet<>();
}

package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_variant_id")
    private UUID id;

    private double originalPrice;
    private Double salePrice;

    private String name;
    private String nameColor;
    private String colorCode;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "product_variant_images",
            joinColumns = @JoinColumn(name = "product_variant_id")
    )
    @Column(name = "image_url", length = 1000)
    @OrderColumn(name = "image_order")
    private List<String> imageUrls = new ArrayList<>();

    private Instant saleStartAt;
    private Instant saleEndAt;

    private int stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}

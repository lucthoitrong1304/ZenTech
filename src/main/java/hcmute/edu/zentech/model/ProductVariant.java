package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
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

    private Instant saleStartAt;
    private Instant saleEndAt;

    private int stockQuantity;

    @Column(nullable = false, columnDefinition = "int default 0")
    private int faultyQuantity;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}

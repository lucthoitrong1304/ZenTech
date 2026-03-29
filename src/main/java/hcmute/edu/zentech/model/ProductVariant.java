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
    private double salePrice;

    // Toàn bộ các field biến thể có thể null hoặc không?
    private String name; // Tên biến thể nếu có.
    private String nameColor; // Màu sắc biến thể.
    private String urlImg; // Ảnh biến thể
    private String colorCode; // Mã màu của biến thể.

    private Instant saleStartAt;
    private Instant saleEndAt;

    private int stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
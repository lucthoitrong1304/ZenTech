package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "product_images")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ImageProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "image_product_id")
    private UUID id;

    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
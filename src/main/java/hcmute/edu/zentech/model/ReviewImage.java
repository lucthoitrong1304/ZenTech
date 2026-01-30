package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "review_images")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ReviewImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_image_id")
    private UUID id;

    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ProductReview productReview;
}
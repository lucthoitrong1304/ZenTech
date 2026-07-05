package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "chat_message_recommendations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recommendation_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "image_key", nullable = false, length = 1000)
    private String imageKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "sale_price", precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

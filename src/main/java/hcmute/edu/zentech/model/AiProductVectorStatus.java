package hcmute.edu.zentech.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ai_product_vector_status",
        indexes = {
                @Index(name = "idx_ai_product_vector_product", columnList = "product_id"),
                @Index(name = "idx_ai_product_vector_variant", columnList = "variant_id"),
                @Index(name = "idx_ai_product_vector_sync", columnList = "sync_status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_product_vector_variant", columnNames = "variant_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProductVectorStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "status_id")
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 24)
    @Builder.Default
    private AiProductVectorSyncStatus syncStatus = AiProductVectorSyncStatus.NOT_SYNCED;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "qdrant_present")
    private Boolean qdrantPresent;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

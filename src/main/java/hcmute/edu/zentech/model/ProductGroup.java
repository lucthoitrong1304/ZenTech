package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "product_groups")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProductGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_group_id")
    private UUID id;

    @Column(nullable = false)
    private String groupName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @UpdateTimestamp
    private Instant updatedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    private Instant deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "productGroup")
    private Set<Product> products = new HashSet<>();
}

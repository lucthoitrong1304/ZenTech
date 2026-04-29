package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Builder.Default
    @OneToMany(mappedBy = "productGroup")
    private Set<Product> products = new HashSet<>();
}

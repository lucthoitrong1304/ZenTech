package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "product_categories")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_id")
    private UUID id;

    @Column(nullable = false)
    private String categoryName;

    private String shortName;

    @Column(nullable = false, columnDefinition = "int default 999")
    private Integer priority = 999;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean visible = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private Set<ProductCategory> children = new HashSet<>();

    @ManyToMany(mappedBy = "categories")
    private Set<Product> productList = new HashSet<>();
}

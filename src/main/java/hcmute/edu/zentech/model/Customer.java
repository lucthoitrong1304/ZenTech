package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_id")
    private UUID id;

    private String fullName;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "customer_id")
    private Set<Address> addressList;

    @OneToOne
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = false, unique = true)
    private AccountUser userInfo;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @OneToOne(cascade = CascadeType.REMOVE, optional = true)
    @JoinColumn(name = "cart_id", referencedColumnName = "cart_id", nullable = true, unique = true)
    private Cart cart;
}
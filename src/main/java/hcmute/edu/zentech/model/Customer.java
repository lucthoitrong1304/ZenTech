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

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = false, unique = true)
    private AccountUser userInfo;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "cart_id", referencedColumnName = "cart_id", nullable = false, unique = true)
    private Cart cart;
}
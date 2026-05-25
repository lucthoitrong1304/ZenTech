package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_vouchers")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class CustomerVoucher {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_voucher_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant issuedAt;

    private Instant usedAt;
}

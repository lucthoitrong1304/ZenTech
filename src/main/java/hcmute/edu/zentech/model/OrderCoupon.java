package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "order_coupons")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class OrderCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_coupon_id")
    private UUID id;

    private String couponCode;

    @Enumerated(EnumType.STRING)
    private CouponType couponType;

    private Double discountValue; // Giá trị giảm (vd: 10% hoặc 50k)
    private Double maxDiscount;   // Giảm tối đa (vd: tối đa 100k)
    private Double appliedAmount; // Số tiền thực tế đã được trừ

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;
}

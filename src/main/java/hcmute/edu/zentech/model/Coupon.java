package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "coupon_id")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    private CouponType type;

    private double discountValue;  // Giá trị giảm (vd: 10 hoặc 50000)
    private double maxDiscount;    // Giảm tối đa (chỉ dùng nếu type là %)
    private double minOrderAmount; // Đơn hàng tối thiểu để được dùng

    // --- CÁC ĐIỀU KIỆN THỜI GIAN ---
    private Instant startAt;
    private Instant endAt;

    // --- QUẢN LÝ SỐ LƯỢNG ---
    private int usageLimit; // Tổng số lần mã này được dùng (vd: 100 lần)

    private int usedCount = 0;

    private boolean active; // Admin có thể tắt nóng mã này
}
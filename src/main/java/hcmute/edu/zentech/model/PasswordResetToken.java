package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token; // Chuỗi token gửi qua email (thường là UUID)

    // Dùng ManyToOne để lỡ user bấm "Quên mật khẩu" nhiều lần thì sinh ra nhiều token vẫn được
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountUser user;

    @Column(nullable = false)
    private Instant expiryDate;
}
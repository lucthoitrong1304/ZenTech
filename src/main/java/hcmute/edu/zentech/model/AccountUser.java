package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id")
    private UUID id;

    private String email;

    private String password;

    private boolean isPasswordSet; // true nếu đã đặt mật khẩu, false nếu là tài khoản Google mới tạo

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean isActive;

    private Instant createdAt;
}
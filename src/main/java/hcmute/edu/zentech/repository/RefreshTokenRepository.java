package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    // Dùng để xóa token khi người dùng bấm Đăng xuất khỏi thiết bị hiện tại
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
    void deleteByToken(String token);

    // Dùng để "Đăng xuất khỏi tất cả thiết bị" (ví dụ khi đổi mật khẩu/bị hack)
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    void deleteByUser(AccountUser user);
}
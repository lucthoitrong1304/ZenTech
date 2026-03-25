package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    // Xóa tất cả token reset pass cũ của user (khi tạo token mới hoặc khi đổi pass thành công)
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.user = :user")
    void deleteByUser(AccountUser user);
}
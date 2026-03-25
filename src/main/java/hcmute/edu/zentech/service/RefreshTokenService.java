package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.RefreshToken;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.RefreshTokenRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    @Value("${application.security.jwt.refresh-expiration}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountUserRepository userRepository;
    private final JwtUtils jwtUtils;

    public String createRefreshToken(UUID userId, String email) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(userRepository.findById(userId).get()) // Khớp với field 'user' trong Entity
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .build();

        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    @Transactional
    public AuthResponse generateNewAccessToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh Token không tồn tại!"));

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh Token đã hết hạn. Vui lòng đăng nhập lại!");
        }

        // Fix lỗi: Sửa getAccountUser() thành getUser()
        AccountUser user = refreshToken.getUser();
        refreshTokenRepository.delete(refreshToken);

        CustomUserDetails userDetails = CustomUserDetails.build(user);
        String newAccessToken = jwtUtils.generateJwtToken(userDetails);
        String newRefreshToken = createRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .accountId(user.getId().toString())
                .email(user.getEmail())
                .roles(userDetails.getAuthorities().stream().map(Object::toString).toList())
                .build();
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }
}
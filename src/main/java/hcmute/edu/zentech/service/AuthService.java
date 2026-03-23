package hcmute.edu.zentech.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import hcmute.edu.zentech.dto.request.*;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CartRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.PasswordResetTokenRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final AccountUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    private final GoogleAuthService googleAuthService;
    private final EmailService emailService;

    private final PasswordResetTokenRepository resetTokenRepository;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Transactional
    public void registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng!");
        }

        AccountUser user = AccountUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        Customer customer = Customer.builder()
                .userInfo(user)
                .fullName(request.getFullName() != null ? request.getFullName() : "Khách hàng mới")
                .build();
        customerRepository.save(customer);

        log.info("Đã đăng ký tài khoản mới: {}", request.getEmail());
    }

    public AuthResponse authenticate(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        return generateAuthResponse((CustomUserDetails) authentication.getPrincipal());
    }

    // --- MODULE: ĐĂNG NHẬP BẰNG GOOGLE ---
    @Transactional
    public AuthResponse authenticateWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = googleAuthService.verifyToken(idTokenString);
        if (payload == null) {
            throw new RuntimeException("Token Google không hợp lệ hoặc đã hết hạn!");
        }

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        Optional<AccountUser> userOptional = userRepository.findByEmail(email);
        AccountUser user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
        } else {
            // Lần đầu đăng nhập bằng Google -> Auto đăng ký
            user = AccountUser.builder()
                    .email(email)
                    // Mật khẩu random vì user Google không xài pass này để login thường
                    .password(passwordEncoder.encode("GOOGLE_SSO_" + UUID.randomUUID().toString()))
                    .role(Role.CUSTOMER)
                    .isActive(true)
                    .build();
            user = userRepository.save(user);

            Customer customer = Customer.builder()
                    .userInfo(user)
                    .fullName(name != null ? name : "Người dùng Google")
                    .build();
            customerRepository.save(customer);

            log.info("Tạo mới tài khoản từ Google Auth: {}", email);
        }

        return generateAuthResponse(CustomUserDetails.build(user));
    }

    // --- MODULE: QUÊN MẬT KHẨU (GỬI EMAIL) ---
    public void forgotPassword(String email) {
        AccountUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này!"));

        // 1. Tạo một token reset (UUID)
        String resetTokenString = java.util.UUID.randomUUID().toString();

        // 2. Lưu token vào DB, set thời gian hết hạn là 10 phút tính từ bây giờ
        PasswordResetToken tokenEntity = PasswordResetToken.builder()
                .token(resetTokenString)
                .user(user)
                .expiryDate(java.time.Instant.now().plus(java.time.Duration.ofMinutes(10)))
                .build();
        resetTokenRepository.save(tokenEntity);

        // 3. Tạo link trỏ về Frontend
        String resetLink = frontendUrl + "/reset-password?token=" + resetTokenString;

        // 4. Bắn mail (Nhớ sửa lại giao diện Zentech trong EmailService nha)
        emailService.sendResetPasswordEmail(user.getEmail(), resetLink);
        log.info("Đã gửi email khôi phục mật khẩu tới: {}", email);
    }

    // --- MODULE: ĐẶT LẠI MẬT KHẨU MỚI ---
    @Transactional
    public void resetPassword(String token, String newPassword) {
        // 1. Tìm token trong DB
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Link khôi phục không hợp lệ hoặc không tồn tại!"));

        // 2. Kiểm tra xem token đã hết hạn chưa (hơn 10 phút)
        if (resetToken.getExpiryDate().isBefore(java.time.Instant.now())) {
            resetTokenRepository.delete(resetToken);
            throw new RuntimeException("Link khôi phục đã hết hạn. Vui lòng yêu cầu lại!");
        }

        // 3. Lấy User ra và cập nhật mật khẩu
        AccountUser user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 4. Xóa token đi để không dùng lại được nữa
        resetTokenRepository.delete(resetToken);

        log.info("Đã đặt lại mật khẩu thành công cho: {}", user.getEmail());
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        return refreshTokenService.generateNewAccessToken(request.getRefreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.deleteByToken(refreshToken);
    }

    // --- UTILS ---
    private AuthResponse generateAuthResponse(CustomUserDetails userDetails) {
        String accessToken = jwtUtils.generateJwtToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(userDetails.getId(), userDetails.getEmail());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accountId(userDetails.getId().toString())
                .email(userDetails.getEmail())
                .roles(roles)
                .build();
    }
}
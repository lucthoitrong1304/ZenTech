package hcmute.edu.zentech.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import hcmute.edu.zentech.dto.request.*;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CartRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.PasswordResetTokenRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final EmployeeRepository employeeRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    private final GoogleAuthService googleAuthService;
    private final EmailService emailService;

    private final PasswordResetTokenRepository resetTokenRepository;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    // --- MODULE: ĐĂNG KÝ ---
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
                .createdAt(Instant.now())
                .isPasswordSet(true)
                .build();
        user = userRepository.save(user);

        Customer customer = Customer.builder()
                .userInfo(user)
                .fullName(request.getFullName() != null ? request.getFullName() : "Khách hàng mới")
                .build();
        customerRepository.save(customer);

        log.info("Đã đăng ký tài khoản mới: {}", request.getEmail());
    }

    // --- MODULE: ĐĂNG NHẬP THƯỜNG ---
    public AuthResponse authenticate(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            if (!userDetails.isEnabled()) {
                throw new RuntimeException("Tài khoản của bạn đã bị khóa hoặc chưa được kích hoạt.");
            }

            log.info("Người dùng {} đăng nhập thành công", request.getEmail());
            return generateAuthResponse(userDetails);

        } catch (BadCredentialsException ex) {
            throw new RuntimeException("Email hoặc mật khẩu không chính xác.");
        } catch (DisabledException ex) {
            throw new RuntimeException("Tài khoản đã bị vô hiệu hóa.");
        } catch (LockedException ex) {
            throw new RuntimeException("Tài khoản hiện đang bị tạm khóa.");
        } catch (Exception ex) {
            log.error("Lỗi đăng nhập cho email {}: {}", request.getEmail(), ex.getMessage());
            throw new RuntimeException("Có lỗi hệ thống xảy ra trong quá trình đăng nhập.");
        }
    }

    // --- MODULE: ĐĂNG NHẬP BẰNG GOOGLE ---
    @Transactional
    public AuthResponse authenticateWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = googleAuthService.verifyToken(idTokenString);
        if (payload == null) {
            log.warn("Cảnh báo: Thử nghiệm đăng nhập Google với Token không hợp lệ.");
            throw new RuntimeException("Xác thực Google thất bại hoặc Token đã hết hạn.");
        }

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        Optional<AccountUser> userOptional = userRepository.findByEmail(email);
        AccountUser user;

        if (userOptional.isPresent()) {
            user = userOptional.get();

            if (!user.isActive()) {
                log.info("Từ chối đăng nhập Google cho tài khoản bị khóa: {}", email);
                throw new RuntimeException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ hỗ trợ.");
            }

            if (user.getRole() == Role.CUSTOMER) {
                Customer customer = customerRepository.findByUserInfo_Id(user.getId()).orElse(null);
                if (customer != null && (customer.getImageUrl() == null || customer.getImageUrl().trim().isEmpty()) && pictureUrl != null) {
                    customer.setImageUrl(pictureUrl);
                    customerRepository.save(customer);
                    log.info("Cập nhật ảnh đại diện từ Google cho khách hàng cũ: {}", email);
                } else {
                    log.info("Khách hàng Google cũ đăng nhập: {}", email);
                }
            } else {
                Employee employee = employeeRepository.findByUserInfo_Id(user.getId()).orElse(null);
                if (employee == null) {
                    employee = new Employee();
                    employee.setUserInfo(user);
                    employee.setFullName(name != null ? name : "Nhân sự hệ thống");
                    employee.setImageUrl(pictureUrl);
                    employeeRepository.save(employee);
                    log.info("Tạo mới hồ sơ nhân sự cho tài khoản có sẵn: {}", email);
                } else if ((employee.getImageUrl() == null || employee.getImageUrl().trim().isEmpty()) && pictureUrl != null) {
                    employee.setImageUrl(pictureUrl);
                    employeeRepository.save(employee);
                    log.info("Cập nhật ảnh đại diện từ Google cho nhân viên cũ: {}", email);
                } else {
                    log.info("Nhân viên Google cũ đăng nhập: {}", email);
                }
            }
        } else {
            user = AccountUser.builder()
                    .email(email)
                    .password(passwordEncoder.encode("GOOGLE_SSO_" + UUID.randomUUID().toString()))
                    .role(Role.CUSTOMER)
                    .isActive(true)
                    .isPasswordSet(false)
                    .createdAt(Instant.now())
                    .build();
            user = userRepository.save(user);

            Customer customer = Customer.builder()
                    .userInfo(user)
                    .fullName(name != null ? name : "Người dùng Google")
                    .imageUrl(pictureUrl)
                    .build();
            customerRepository.save(customer);

            log.info("Tạo mới tài khoản từ Google Auth thành công: {}", email);
        }

        return generateAuthResponse(CustomUserDetails.build(user));
    }

    // --- MODULE: QUÊN MẬT KHẨU (GỬI EMAIL) ---
    public void forgotPassword(String email) {
        AccountUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này!"));

        if (!user.isPasswordSet()) {
            throw new RuntimeException("Tài khoản này đang sử dụng đăng nhập Google. Vui lòng đăng nhập bằng Google trước, sau đó đặt mật khẩu trong hồ sơ.");
        }

        String resetTokenString = java.util.UUID.randomUUID().toString();

        PasswordResetToken tokenEntity = PasswordResetToken.builder()
                .token(resetTokenString)
                .user(user)
                .expiryDate(java.time.Instant.now().plus(java.time.Duration.ofMinutes(10)))
                .build();
        resetTokenRepository.save(tokenEntity);

        String resetLink = frontendUrl + "/reset-password?token=" + resetTokenString;

        emailService.sendResetPasswordEmail(user.getEmail(), resetLink);
        log.info("Đã gửi email khôi phục mật khẩu tới: {}", email);
    }

    // --- MODULE: ĐẶT LẠI MẬT KHẨU MỚI ---
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Link khôi phục không hợp lệ hoặc không tồn tại!"));

        if (resetToken.getExpiryDate().isBefore(java.time.Instant.now())) {
            resetTokenRepository.delete(resetToken);
            throw new RuntimeException("Token đã hết hạn!");
        }

        AccountUser user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordSet(true);
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

        // Query lên để lấy thuộc tính isPasswordSet từ Database
        AccountUser user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        String imageUrl = null;
        if (user.getRole() == Role.CUSTOMER) {
            Customer customer = customerRepository.findByUserInfo_Id(user.getId()).orElse(null);
            imageUrl = customer != null ? customer.getImageUrl() : null;
        } else {
            Employee employee = employeeRepository.findByUserInfo_Id(user.getId()).orElse(null);
            imageUrl = employee != null ? employee.getImageUrl() : null;
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accountId(userDetails.getId().toString())
                .email(userDetails.getEmail())
                .roles(roles)
                .imageUrl(imageUrl)
                .isPasswordSet(user.isPasswordSet()) // ---> THÊM DÒNG NÀY VÀO NÀY
                .build();
    }

    // --- 8. ĐỔI MẬT KHẨU / ĐẶT MẬT KHẨU LẦN ĐẦU ---
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        java.util.UUID accountId = hcmute.edu.zentech.security.SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng nhập.");
        }

        AccountUser user = userRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

        if (user.isPasswordSet()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
                throw new RuntimeException("Vui lòng nhập mật khẩu hiện tại.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new RuntimeException("Mật khẩu hiện tại không đúng.");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSet(true);
        userRepository.save(user);
    }
}

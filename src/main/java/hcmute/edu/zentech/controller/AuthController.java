package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.*;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    // --- 1. ĐĂNG KÝ ---
    @PostMapping("/register")
    @TrackActivity(action = ActivityAction.CREATE_ACCOUNT, area = ActivityArea.CUSTOMER, module = "AUTH", targetType = "ACCOUNT", severity = ActivitySeverity.IMPORTANT, summary = "Khách hàng đăng ký tài khoản")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        authService.registerUser(request);
        return ResponseEntity.ok("Đăng ký tài khoản thành công!");
    }

    // --- 2. ĐĂNG NHẬP (THƯỜNG) ---
    @PostMapping("/login")
    @TrackActivity(action = ActivityAction.LOGIN, failureAction = ActivityAction.LOGIN_FAILED, area = ActivityArea.SYSTEM, module = "AUTH", targetType = "ACCOUNT", severity = ActivitySeverity.SECURITY, summary = "Đăng nhập hệ thống", logOnFailure = true)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    // --- 3. ĐĂNG NHẬP BẰNG GOOGLE ---
    @PostMapping("/google")
    @TrackActivity(action = ActivityAction.LOGIN, failureAction = ActivityAction.LOGIN_FAILED, area = ActivityArea.SYSTEM, module = "AUTH", targetType = "ACCOUNT", severity = ActivitySeverity.SECURITY, summary = "Đăng nhập Google", logOnFailure = true)
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        // Đã sửa thành authenticateWithGoogle cho khớp AuthService
        return ResponseEntity.ok(authService.authenticateWithGoogle(request.getToken()));
    }

    // --- 4. LÀM MỚI TOKEN (ROTATION) ---
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    // --- 5. ĐĂNG XUẤT ---
    @PostMapping("/logout")
    @TrackActivity(action = ActivityAction.LOGOUT, area = ActivityArea.SYSTEM, module = "AUTH", targetType = "ACCOUNT", severity = ActivitySeverity.SECURITY, summary = "Đăng xuất hệ thống")
    public ResponseEntity<String> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok("Đăng xuất thành công!");
    }

    // --- 6. QUÊN MẬT KHẨU (GỬI LINK/OTP) ---
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Đã sửa thành forgotPassword cho khớp AuthService
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok("Yêu cầu đã được xử lý! Vui lòng kiểm tra email của bạn.");
    }

    // --- 7. ĐẶT LẠI MẬT KHẨU MỚI ---
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Đổi mật khẩu thành công! Bạn có thể đăng nhập ngay bây giờ.");
    }

    // --- 8. ĐỔI MẬT KHẨU HOẶC ĐẶT MẬT KHẨU LẦN ĐẦU ---
    @PutMapping("/password")
    @TrackActivity(action = ActivityAction.PASSWORD_CHANGED, area = ActivityArea.SYSTEM, module = "AUTH", targetType = "ACCOUNT", severity = ActivitySeverity.SECURITY, summary = "Đổi mật khẩu")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Cập nhật mật khẩu thành công.");
    }
}

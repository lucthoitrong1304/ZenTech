package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.LoginRequest;
import hcmute.edu.zentech.dto.request.RegisterRequest;
import hcmute.edu.zentech.dto.request.TokenRefreshRequest;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AccountUser> register(
            @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authService.registerUser(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(
            @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody TokenRefreshRequest request
    ) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }
}

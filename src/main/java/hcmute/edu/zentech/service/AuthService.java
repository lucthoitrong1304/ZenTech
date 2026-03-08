package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.LoginRequest;
import hcmute.edu.zentech.dto.request.RegisterRequest;
import hcmute.edu.zentech.dto.request.TokenRefreshRequest;
import hcmute.edu.zentech.dto.response.AuthResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.RefreshToken;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.security.jwt.JwtService;
import hcmute.edu.zentech.security.model.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountUserRepository accountUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public AccountUser registerUser(RegisterRequest request) {
        if (accountUserRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("User with this email already exists");
        }

        AccountUser user = new AccountUser();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setActive(true);
        user.setCreatedAt(Instant.now());

        return accountUserRepository.save(user);
    }

    public AuthResponse authenticate(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = accountUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        var customUserDetails = new CustomUserDetails(user);

        var jwtToken = jwtService.generateToken(customUserDetails);
        var refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtService.generateToken(new CustomUserDetails(user));
                    return AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(request.getRefreshToken())
                            .build();
                }).orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }
}


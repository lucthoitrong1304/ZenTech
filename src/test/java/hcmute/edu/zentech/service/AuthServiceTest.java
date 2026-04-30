package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.PasswordResetToken;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CartRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.PasswordResetTokenRepository;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AccountUserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                userRepository,
                customerRepository,
                cartRepository,
                passwordEncoder,
                jwtUtils,
                refreshTokenService,
                googleAuthService,
                emailService,
                resetTokenRepository
        );
    }

    @Test
    void resetPasswordActivatesAccountAndDeletesToken() {
        AccountUser user = AccountUser.builder()
                .id(UUID.randomUUID())
                .email("employee@example.com")
                .password("old-password")
                .role(Role.EMPLOYEE)
                .isActive(false)
                .createdAt(Instant.parse("2026-04-30T00:00:00Z"))
                .build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("reset-token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(60))
                .build();

        when(resetTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-secret")).thenReturn("encoded-secret");

        authService.resetPassword("reset-token", "new-secret");

        assertThat(user.getPassword()).isEqualTo("encoded-secret");
        assertThat(user.isActive()).isTrue();
        verify(userRepository).save(user);
        verify(resetTokenRepository).delete(token);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("expired-token")
                .user(AccountUser.builder().email("employee@example.com").build())
                .expiryDate(Instant.now().minusSeconds(60))
                .build();

        when(resetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "new-secret"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("hết hạn");

        verify(resetTokenRepository).delete(token);
        verify(userRepository, never()).save(token.getUser());
    }
}

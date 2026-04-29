package dev.knalis.auth.service.token;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.dto.request.ConfirmPasswordResetRequest;
import dev.knalis.auth.dto.request.RequestPasswordResetRequest;
import dev.knalis.auth.entity.PasswordResetToken;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.factory.token.PasswordResetTokenFactory;
import dev.knalis.auth.factory.token.RefreshTokenFactory;
import dev.knalis.auth.repository.PasswordResetTokenRepository;
import dev.knalis.auth.repository.RefreshTokenRepository;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.service.common.TokenHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        tokenService = new TokenService(
                refreshTokenRepository,
                passwordResetTokenRepository,
                userRepository,
                authProperties,
                new TokenHashService(),
                new RefreshTokenFactory(),
                new PasswordResetTokenFactory(),
                passwordEncoder
        );
    }

    @Test
    void requestPasswordResetDoesNothingForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        tokenService.requestPasswordReset(new RequestPasswordResetRequest("missing@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void requestPasswordResetRevokesOutstandingTokensBeforeCreatingNewOne() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        tokenService.requestPasswordReset(new RequestPasswordResetRequest(user.getEmail()));

        verify(passwordResetTokenRepository).revokeAllActiveByUserId(eq(user.getId()), any(Instant.class));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void confirmPasswordResetRevokesOutstandingResetAndRefreshTokens() {
        UUID userId = UUID.randomUUID();
        String rawToken = "reset-token";
        String tokenHash = new TokenHashService().hash(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(userId);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(300));

        User user = new User();
        user.setId(userId);
        user.setPasswordHash("old-hash");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        tokenService.confirmPasswordReset(new ConfirmPasswordResetRequest(rawToken, "new-password"));

        verify(passwordResetTokenRepository).revokeAllActiveByUserId(eq(userId), any(Instant.class));
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(userId), any(Instant.class));
    }
}

package dev.knalis.auth.service.token;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.dto.request.ConfirmPasswordResetRequest;
import dev.knalis.auth.dto.request.RequestPasswordResetRequest;
import dev.knalis.auth.entity.PasswordResetToken;
import dev.knalis.auth.entity.RefreshToken;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.exception.PasswordResetTokenExpiredException;
import dev.knalis.auth.exception.PasswordResetTokenNotFoundException;
import dev.knalis.auth.exception.RefreshTokenExpiredException;
import dev.knalis.auth.exception.RefreshTokenNotFoundException;
import dev.knalis.auth.exception.UserNotFoundException;
import dev.knalis.auth.factory.token.PasswordResetTokenFactory;
import dev.knalis.auth.factory.token.RefreshTokenFactory;
import dev.knalis.auth.repository.PasswordResetTokenRepository;
import dev.knalis.auth.repository.RefreshTokenRepository;
import dev.knalis.auth.repository.UserRepository;
import dev.knalis.auth.service.common.TokenHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final AuthProperties authProperties;
    private final TokenHashService tokenHashService;
    private final RefreshTokenFactory refreshTokenFactory;
    private final PasswordResetTokenFactory passwordResetTokenFactory;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional
    public CreatedRefreshToken createRefreshToken(UUID userId, boolean mfaVerified, MfaMethodType mfaMethod) {
        String rawToken = generateTokenValue();
        String tokenHash = tokenHashService.hash(rawToken);
        
        RefreshToken token = refreshTokenFactory.newToken(
                userId,
                tokenHash,
                Instant.now().plus(authProperties.getRefreshTokenTtl()),
                mfaVerified,
                mfaMethod
        );
        
        RefreshToken saved = refreshTokenRepository.save(token);
        return new CreatedRefreshToken(saved, rawToken);
    }
    
    @Transactional(readOnly = true)
    public RefreshToken requireValidRefreshToken(String rawToken) {
        String tokenHash = tokenHashService.hash(rawToken);
        
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(RefreshTokenNotFoundException::new);
        
        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshTokenExpiredException();
        }
        
        return refreshToken;
    }
    
    @Transactional
    public CreatedRefreshToken rotateRefreshToken(String rawToken) {
        RefreshToken existing = requireValidRefreshToken(rawToken);
        return rotateRefreshToken(existing);
    }

    @Transactional
    public CreatedRefreshToken rotateRefreshToken(RefreshToken existing) {
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        
        return createRefreshToken(existing.getUserId(), existing.isMfaVerified(), existing.getMfaMethod());
    }
    
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String tokenHash = tokenHashService.hash(rawToken);
        
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(existing -> {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
        });
    }
    
    @Transactional
    public void revokeAllRefreshTokens(UUID userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
    }
    
    @Transactional
    public void requestPasswordReset(RequestPasswordResetRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (user == null) {
            return;
        }
        
        passwordResetTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
        
        String rawToken = generateTokenValue();
        String tokenHash = tokenHashService.hash(rawToken);
        
        PasswordResetToken resetToken = passwordResetTokenFactory.newToken(
                user.getId(),
                tokenHash,
                Instant.now().plus(authProperties.getPasswordResetTokenTtl())
        );
        
        passwordResetTokenRepository.save(resetToken);
    }
    
    @Transactional
    public void confirmPasswordReset(ConfirmPasswordResetRequest request) {
        String tokenHash = tokenHashService.hash(request.resetToken());
        
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(PasswordResetTokenNotFoundException::new);
        
        if (token.isUsed() || token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new PasswordResetTokenExpiredException();
        }
        
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(UserNotFoundException::new);
        
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setForcePasswordChange(false);
        userRepository.save(user);
        
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
        
        revokeAllRefreshTokens(user.getId());
    }
    
    private String generateTokenValue() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    public record CreatedRefreshToken(
            RefreshToken entity,
            String rawToken
    ) {
    }
}

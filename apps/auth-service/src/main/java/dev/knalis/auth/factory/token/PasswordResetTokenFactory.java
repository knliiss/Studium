package dev.knalis.auth.factory.token;

import dev.knalis.auth.entity.PasswordResetToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PasswordResetTokenFactory {
    
    public PasswordResetToken newToken(UUID userId, String tokenHash, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setUsed(false);
        token.setRevoked(false);
        return token;
    }
}
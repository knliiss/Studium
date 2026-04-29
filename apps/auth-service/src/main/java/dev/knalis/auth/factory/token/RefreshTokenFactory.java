package dev.knalis.auth.factory.token;

import dev.knalis.auth.entity.RefreshToken;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RefreshTokenFactory {
    
    public RefreshToken newToken(
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            boolean mfaVerified,
            MfaMethodType mfaMethod
    ) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setRevoked(false);
        token.setMfaVerified(mfaVerified);
        token.setMfaMethod(mfaMethod);
        return token;
    }
}

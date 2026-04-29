package dev.knalis.auth.service.mfa;

import dev.knalis.auth.entity.User;
import dev.knalis.auth.exception.MfaMethodNotConfiguredException;
import dev.knalis.auth.exception.MfaVerificationFailedException;
import dev.knalis.auth.mfa.entity.MfaChallenge;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.mfa.entity.UserMfaMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TotpMfaMethodHandler implements MfaMethodHandler {
    
    private final MfaSecretEncryptionService mfaSecretEncryptionService;
    private final TotpService totpService;
    
    @Override
    public MfaMethodType getMethodType() {
        return MfaMethodType.TOTP;
    }
    
    @Override
    public void verify(MfaChallenge challenge, User user, UserMfaMethod method, String code) {
        if (method.getSecretEncrypted() == null || method.getSecretEncrypted().isBlank()) {
            throw new MfaMethodNotConfiguredException(MfaMethodType.TOTP);
        }
        
        String secret = mfaSecretEncryptionService.decrypt(method.getSecretEncrypted());
        if (!totpService.verifyCode(secret, normalizeCode(code))) {
            throw new MfaVerificationFailedException();
        }
    }
    
    private String normalizeCode(String code) {
        return code == null ? null : code.trim();
    }
}

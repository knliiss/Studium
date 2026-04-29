package dev.knalis.auth.service.mfa;

import dev.knalis.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {
    
    @Test
    void shouldGenerateSecretAndOtpauthUri() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getMfa().setIssuer("Studium");
        TotpService totpService = new TotpService(authProperties);
        
        String secret = totpService.generateSecret();
        String uri = totpService.buildOtpauthUri("alice", secret);
        
        assertNotNull(secret);
        assertTrue(secret.length() >= 16);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("issuer=Studium"));
    }
    
    @Test
    void shouldRejectObviouslyInvalidCode() {
        TotpService totpService = new TotpService(new AuthProperties());
        String secret = totpService.generateSecret();
        
        assertFalse(totpService.verifyCode(secret, "abcdef"));
        assertFalse(totpService.verifyCode(secret, "12"));
    }
}

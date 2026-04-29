package dev.knalis.auth.service.mfa;

import dev.knalis.auth.config.AuthProperties;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
public class TotpService {
    
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base32 base32 = new Base32();
    
    public TotpService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }
    
    public String generateSecret() {
        byte[] secret = new byte[20];
        secureRandom.nextBytes(secret);
        return base32.encodeToString(secret).replace("=", "");
    }
    
    public String buildOtpauthUri(String accountName, String secret) {
        String issuer = authProperties.getMfa().getIssuer();
        String label = urlEncode(issuer + ":" + accountName);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + authProperties.getMfa().getTotpDigits()
                + "&period=" + authProperties.getMfa().getTotpTimeStepSeconds();
    }
    
    public boolean verifyCode(String secret, String code) {
        if (code == null || !code.matches("\\d{" + authProperties.getMfa().getTotpDigits() + "}")) {
            return false;
        }
        
        long currentStep = Instant.now().getEpochSecond() / authProperties.getMfa().getTotpTimeStepSeconds();
        byte[] secretBytes = base32.decode(secret.toUpperCase(Locale.ROOT));
        int window = authProperties.getMfa().getTotpWindowSteps();
        
        for (int offset = -window; offset <= window; offset++) {
            if (generateCode(secretBytes, currentStep + offset).equals(code)) {
                return true;
            }
        }
        return false;
    }
    
    private String generateCode(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, authProperties.getMfa().getTotpDigits());
            return String.format("%0" + authProperties.getMfa().getTotpDigits() + "d", otp);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to generate TOTP code", exception);
        }
    }
    
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

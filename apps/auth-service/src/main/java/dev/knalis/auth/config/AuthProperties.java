package dev.knalis.auth.config;

import dev.knalis.shared.security.properties.JwtResourceServerProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    
    @Valid
    private final Jwt jwt = new Jwt();
    
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    private Duration refreshTokenTtl = Duration.ofDays(14);
    private Duration passwordResetTokenTtl = Duration.ofHours(1);
    
    @Valid
    private final Mfa mfa = new Mfa();

    @Valid
    private final RateLimit rateLimit = new RateLimit();
    
    @Valid
    private final Owner owner = new Owner();
    
    @Getter
    @Setter
    public static class Jwt extends JwtResourceServerProperties {

        @NotBlank
        private String privateKeyPath;
    }
    
    @Getter
    @Setter
    public static class Owner {
        
        private boolean seedEnabled;
        
        private String username;
        
        private String email;
        
        private String password;
    }

    @Getter
    @Setter
    public static class RateLimit {

        private boolean enabled = true;

        @NotBlank
        private String redisKeyPrefix = "auth:rate-limit:";

        @Valid
        private final Bucket registerIp = new Bucket(5, Duration.ofMinutes(10));

        @Valid
        private final Bucket loginIp = new Bucket(20, Duration.ofMinutes(5));

        @Valid
        private final Bucket loginUsername = new Bucket(10, Duration.ofMinutes(5));

        @Valid
        private final Bucket passwordResetIp = new Bucket(5, Duration.ofMinutes(15));

        @Valid
        private final Bucket passwordResetEmail = new Bucket(3, Duration.ofMinutes(15));

        @Valid
        private final Bucket mfaDispatch = new Bucket(10, Duration.ofMinutes(10));

        @Valid
        private final Bucket mfaVerify = new Bucket(20, Duration.ofMinutes(10));
    }

    @Getter
    @Setter
    public static class Bucket {

        @Min(1)
        private int maxRequests = 10;

        @NotNull
        private Duration window = Duration.ofMinutes(1);

        public Bucket() {
        }

        public Bucket(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }
    }
    
    @Getter
    @Setter
    public static class Mfa {
        
        private boolean enabled = true;
        
        private Duration challengeTtl = Duration.ofMinutes(5);
        
        private Duration emailCodeTtl = Duration.ofMinutes(10);
        
        private int maxVerificationAttempts = 5;
        
        private int maxEmailDispatches = 3;
        
        private int emailCodeLength = 6;
        
        private int totpDigits = 6;
        
        private int totpTimeStepSeconds = 30;
        
        private int totpWindowSteps = 1;
        
        @NotBlank
        private String issuer = "Studium";
        
        @NotBlank
        private String encryptionKey;
    }
}

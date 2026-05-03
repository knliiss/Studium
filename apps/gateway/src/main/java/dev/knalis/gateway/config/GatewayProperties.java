package dev.knalis.gateway.config;

import dev.knalis.shared.security.properties.JwtResourceServerProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayProperties {
    
    @Valid
    private final Kafka kafka = new Kafka();

    private final Jwt jwt = new Jwt();

    @Valid
    private final RateLimit rateLimit = new RateLimit();
    
    @Valid
    private final Redis redis = new Redis();

    @Valid
    private final Cors cors = new Cors();

    private List<String> publicEndpoints = new ArrayList<>();
    
    @NotEmpty
    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }
    
    @Getter
    @Setter
    public static class Jwt extends JwtResourceServerProperties {
    }

    @Getter
    @Setter
    public static class Kafka {

        @Valid
        private final Topics topics = new Topics();
    }

    @Getter
    @Setter
    public static class Topics {

        @NotBlank
        private String userBanned;

        @NotBlank
        private String userUnbanned;
    }

    @Getter
    @Setter
    public static class RateLimit {

        private boolean enabled = false;

        @Min(1)
        private int maxRequests = 120;

        private Duration window = Duration.ofMinutes(1);
    }
    
    @Getter
    @Setter
    public static class Redis {
        
        @NotBlank
        private String banStateKeyPrefix = "gateway:ban-state:";
        
        @NotBlank
        private String rateLimitKeyPrefix = "gateway:rate-limit:";
    }

    @Getter
    @Setter
    public static class Cors {

        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>();

        @NotEmpty
        private List<String> allowedMethods = new ArrayList<>(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        @NotEmpty
        private List<String> allowedHeaders = new ArrayList<>(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-Request-Id",
                "X-Correlation-Id"
        ));

        private List<String> exposedHeaders = new ArrayList<>(List.of(
                "X-Request-Id",
                "X-Correlation-Id",
                "Content-Disposition"
        ));

        private boolean allowCredentials = false;

        private Duration maxAge = Duration.ofHours(1);
    }
}

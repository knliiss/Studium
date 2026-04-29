package dev.knalis.notification.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.notification.realtime")
public class NotificationRealtimeProperties {
    
    @NotBlank
    private String websocketEndpoint = "/ws/notifications";
    
    @NotBlank
    private String userDestination = "/queue/notifications";
    
    private List<String> allowedOriginPatterns = List.of("*");
    
    @NotBlank
    private String redisTopic = "notifications.live";
    
    private boolean redisFanoutEnabled = true;
    
    @Positive
    private int sendTimeLimitMs = 15_000;
    
    @Positive
    private int sendBufferSizeLimitBytes = 1_048_576;
    
    @Positive
    private int messageSizeLimitBytes = 65_536;
}

package dev.knalis.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification.telegram")
public class TelegramProperties {

    private boolean enabled = false;

    private String botToken;

    private String botUsername;

    private Duration connectTokenTtl = Duration.ofMinutes(15);
}

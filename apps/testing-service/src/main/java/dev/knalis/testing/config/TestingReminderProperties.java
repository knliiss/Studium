package dev.knalis.testing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.testing.reminders")
public class TestingReminderProperties {

    private boolean enabled = true;

    private Duration lookback = Duration.ofMinutes(35);
}

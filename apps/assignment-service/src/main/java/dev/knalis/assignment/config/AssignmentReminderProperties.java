package dev.knalis.assignment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.assignment.reminders")
public class AssignmentReminderProperties {

    private boolean enabled = true;

    private Duration lookback = Duration.ofMinutes(35);
}

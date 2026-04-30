package dev.knalis.schedule.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.schedule.bootstrap")
public class ScheduleBootstrapProperties {

    private boolean enabled = true;
}

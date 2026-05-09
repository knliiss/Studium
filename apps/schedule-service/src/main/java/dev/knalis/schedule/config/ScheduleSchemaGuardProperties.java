package dev.knalis.schedule.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.schedule.schema-guard")
public class ScheduleSchemaGuardProperties {

    private boolean enabled = true;
    private boolean autoRepair = false;
    private String schema = "schedule";
}

package dev.knalis.schedule.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ScheduleJwtProperties.class,
        ScheduleAuditServiceProperties.class
})
public class ScheduleServiceConfig {
}

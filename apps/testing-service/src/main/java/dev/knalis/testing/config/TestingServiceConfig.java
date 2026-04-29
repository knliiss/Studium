package dev.knalis.testing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        TestingJwtProperties.class,
        TestingEducationServiceProperties.class,
        TestingNotificationServiceProperties.class,
        TestingReminderProperties.class,
        TestingAuditServiceProperties.class
})
public class TestingServiceConfig {
}

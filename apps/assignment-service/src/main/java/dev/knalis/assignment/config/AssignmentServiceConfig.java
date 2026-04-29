package dev.knalis.assignment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AssignmentJwtProperties.class,
        AssignmentFileServiceProperties.class,
        AssignmentEducationServiceProperties.class,
        AssignmentNotificationServiceProperties.class,
        AssignmentReminderProperties.class,
        AssignmentAuditServiceProperties.class
})
public class AssignmentServiceConfig {
}

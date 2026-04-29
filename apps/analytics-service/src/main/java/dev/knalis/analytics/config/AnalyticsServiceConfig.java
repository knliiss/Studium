package dev.knalis.analytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AnalyticsJwtProperties.class,
        AnalyticsEducationServiceProperties.class,
        AnalyticsRiskProperties.class,
        AnalyticsKafkaTopicsProperties.class
})
public class AnalyticsServiceConfig {
}

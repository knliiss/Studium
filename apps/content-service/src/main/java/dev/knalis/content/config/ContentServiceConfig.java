package dev.knalis.content.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ContentJwtProperties.class,
        ContentEducationServiceProperties.class
})
public class ContentServiceConfig {
}


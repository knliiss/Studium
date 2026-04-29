package dev.knalis.education.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EducationJwtProperties.class)
public class EducationServiceConfig {
}

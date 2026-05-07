package dev.knalis.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.content.education-service")
public record ContentEducationServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String sharedSecret
) {
}


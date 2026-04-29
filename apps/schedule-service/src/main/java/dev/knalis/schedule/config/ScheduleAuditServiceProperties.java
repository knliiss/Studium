package dev.knalis.schedule.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.schedule.audit-service")
public class ScheduleAuditServiceProperties {

    @NotBlank
    private String baseUrl = "http://localhost:8090";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(5);

    @NotBlank
    private String sharedSecret;
}

package dev.knalis.profile.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.profile.file-service")
public class ProfileFileServiceProperties {
    
    @NotBlank
    private String baseUrl = "http://localhost:8083";
    
    private Duration connectTimeout = Duration.ofSeconds(2);
    
    private Duration readTimeout = Duration.ofSeconds(5);
}

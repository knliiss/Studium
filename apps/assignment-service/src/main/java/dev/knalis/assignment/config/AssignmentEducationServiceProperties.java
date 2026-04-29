package dev.knalis.assignment.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.assignment.education-service")
public class AssignmentEducationServiceProperties {
    
    @NotBlank
    private String baseUrl;
    
    private Duration connectTimeout = Duration.ofSeconds(2);
    
    private Duration readTimeout = Duration.ofSeconds(5);
    
    @NotBlank
    private String sharedSecret;
}

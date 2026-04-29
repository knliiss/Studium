package dev.knalis.testing.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicsProperties {
    
    @NotBlank
    private String testPublished;
    
    @NotBlank
    private String testStarted;
    
    @NotBlank
    private String testCompleted;
}

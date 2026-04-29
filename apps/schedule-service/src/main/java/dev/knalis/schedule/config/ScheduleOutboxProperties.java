package dev.knalis.schedule.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.kafka.outbox")
public class ScheduleOutboxProperties {
    
    private boolean enabled = true;
    
    @Positive
    private int batchSize = 50;
    
    private Duration publishInterval = Duration.ofSeconds(2);
    
    private Duration processingTimeout = Duration.ofSeconds(30);
    
    private Duration sendTimeout = Duration.ofSeconds(10);
    
    @Positive
    private int maxAttempts = 10;
    
    private Duration initialRetryDelay = Duration.ofSeconds(1);
    
    private Duration maxRetryDelay = Duration.ofMinutes(5);
    
    private Duration publishedRetention = Duration.ofDays(7);
    
    private Duration cleanupInterval = Duration.ofHours(1);
}

package dev.knalis.file.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.file.cleanup")
public class FileCleanupProperties {
    
    private boolean enabled = true;
    
    private Duration uploadedRetention = Duration.ofHours(24);
    
    private Duration orphanedRetention = Duration.ofDays(7);
    
    private Duration rejectedRetention = Duration.ofDays(7);
    
    private Duration deletedMetadataRetention = Duration.ofDays(14);
    
    private boolean dryRun = false;
    
    @Positive
    private int batchSize = 100;
    
    private Duration scheduleDelay = Duration.ofMinutes(30);
}

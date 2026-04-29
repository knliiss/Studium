package dev.knalis.file.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.file.quota")
public class FileQuotaProperties {
    
    @Positive
    private long maxFilesPerUser = 500L;
    
    @Positive
    private long maxAvatarFilesPerUser = 10L;
    
    @Positive
    private long maxTotalBytesPerUser = 524_288_000L;
}

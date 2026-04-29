package dev.knalis.file.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.file.storage")
public class FileStorageProperties {
    
    @NotBlank
    private String endpoint;
    
    @NotBlank
    private String accessKey;
    
    @NotBlank
    private String secretKey;
    
    @NotBlank
    private String publicBucket;
    
    @NotBlank
    private String privateBucket;
}

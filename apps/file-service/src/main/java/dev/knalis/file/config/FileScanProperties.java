package dev.knalis.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.file.scan")
public class FileScanProperties {
    
    private boolean enabled = false;
}

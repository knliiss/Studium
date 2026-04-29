package dev.knalis.auth.config;

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
    private String userRegistered;
    
    @NotBlank
    private String userEmailChanged;
    
    @NotBlank
    private String userUsernameChanged;
    
    @NotBlank
    private String userBanned;
    
    @NotBlank
    private String userUnbanned;
    
}

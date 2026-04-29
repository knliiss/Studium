package dev.knalis.assignment.config;

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
    private String assignmentCreated;
    
    @NotBlank
    private String assignmentUpdated;
    
    @NotBlank
    private String gradeAssigned;
    
    @NotBlank
    private String assignmentOpened;
    
    @NotBlank
    private String assignmentSubmitted;
    
    @NotBlank
    private String deadlineMissed;
}

package dev.knalis.schedule.config;

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
    private String scheduleOverrideCreated;
    
    @NotBlank
    private String scheduleLessonCancelled;
    
    @NotBlank
    private String scheduleLessonReplaced;
    
    @NotBlank
    private String scheduleExtraLessonCreated;
}

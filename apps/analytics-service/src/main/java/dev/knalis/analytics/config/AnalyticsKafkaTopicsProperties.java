package dev.knalis.analytics.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.analytics.kafka.topics")
public class AnalyticsKafkaTopicsProperties {
    
    @NotBlank
    private String lectureOpened;
    
    @NotBlank
    private String topicOpened;
    
    @NotBlank
    private String assignmentOpened;
    
    @NotBlank
    private String assignmentSubmitted;
    
    @NotBlank
    private String deadlineMissed;
    
    @NotBlank
    private String testStarted;
    
    @NotBlank
    private String testCompleted;
    
    @NotBlank
    private String assignmentCreated;
    
    @NotBlank
    private String gradeAssigned;
    
    @NotBlank
    private String testPublished;
}

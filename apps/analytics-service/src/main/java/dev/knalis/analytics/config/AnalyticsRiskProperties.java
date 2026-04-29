package dev.knalis.analytics.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.analytics.risk")
public class AnalyticsRiskProperties {
    
    @PositiveOrZero
    private int inactivityDaysForMediumRisk = 7;
    
    @PositiveOrZero
    private int inactivityDaysForHighRisk = 14;
    
    @Min(0)
    @Max(100)
    private int lowAverageScoreThreshold = 60;
    
    @PositiveOrZero
    private int highRiskMissedDeadlinesThreshold = 3;
    
    @PositiveOrZero
    private int mediumRiskMissedDeadlinesThreshold = 1;
    
    @PositiveOrZero
    private int lateSubmissionPenalty = 10;
    
    @PositiveOrZero
    private int missedDeadlinePenalty = 25;
    
    @PositiveOrZero
    private int performanceTrendDeltaThreshold = 5;
}

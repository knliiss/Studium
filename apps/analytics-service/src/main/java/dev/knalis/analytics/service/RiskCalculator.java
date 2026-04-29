package dev.knalis.analytics.service;

import dev.knalis.analytics.config.AnalyticsRiskProperties;
import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RiskCalculator {
    
    private final AnalyticsRiskProperties properties;
    
    public RiskLevel calculate(StudentProgressSnapshot snapshot, Instant referenceTime) {
        long inactivityDays = calculateInactivityDays(snapshot.getLastActivityAt(), referenceTime);
        double averageScore = snapshot.getAverageScore() == null ? 100.0 : snapshot.getAverageScore();
        
        boolean highRisk = inactivityDays >= properties.getInactivityDaysForHighRisk()
                || snapshot.getMissedDeadlinesCount() >= properties.getHighRiskMissedDeadlinesThreshold()
                || averageScore < highRiskAverageScoreThreshold()
                || (snapshot.getPerformanceTrend() == PerformanceTrend.DECLINING
                && averageScore < properties.getLowAverageScoreThreshold());
        if (highRisk) {
            return RiskLevel.HIGH;
        }
        
        boolean mediumRisk = inactivityDays >= properties.getInactivityDaysForMediumRisk()
                || snapshot.getMissedDeadlinesCount() >= properties.getMediumRiskMissedDeadlinesThreshold()
                || averageScore < properties.getLowAverageScoreThreshold()
                || snapshot.getPerformanceTrend() == PerformanceTrend.DECLINING;
        if (mediumRisk) {
            return RiskLevel.MEDIUM;
        }
        
        return RiskLevel.LOW;
    }
    
    private long calculateInactivityDays(Instant lastActivityAt, Instant referenceTime) {
        if (lastActivityAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Duration.between(lastActivityAt, referenceTime).toDays());
    }
    
    private double highRiskAverageScoreThreshold() {
        return Math.max(0, properties.getLowAverageScoreThreshold() - 15.0);
    }
}

package dev.knalis.analytics.service;

import dev.knalis.analytics.config.AnalyticsRiskProperties;
import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskCalculatorTest {
    
    private final AnalyticsRiskProperties properties = new AnalyticsRiskProperties();
    private final RiskCalculator riskCalculator = new RiskCalculator(properties);
    
    @Test
    void calculateReturnsHighForMultipleStrongSignals() {
        StudentProgressSnapshot snapshot = new StudentProgressSnapshot();
        snapshot.setAverageScore(40.0);
        snapshot.setMissedDeadlinesCount(3);
        snapshot.setPerformanceTrend(PerformanceTrend.DECLINING);
        snapshot.setLastActivityAt(Instant.now().minusSeconds(20L * 24 * 3600));
        
        assertEquals(RiskLevel.HIGH, riskCalculator.calculate(snapshot, Instant.now()));
    }
    
    @Test
    void calculateReturnsLowForHealthySnapshot() {
        StudentProgressSnapshot snapshot = new StudentProgressSnapshot();
        snapshot.setAverageScore(88.0);
        snapshot.setMissedDeadlinesCount(0);
        snapshot.setPerformanceTrend(PerformanceTrend.STABLE);
        snapshot.setLastActivityAt(Instant.now().minusSeconds(24 * 3600));
        
        assertEquals(RiskLevel.LOW, riskCalculator.calculate(snapshot, Instant.now()));
    }
}

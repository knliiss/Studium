package dev.knalis.analytics.service;

import dev.knalis.analytics.config.AnalyticsRiskProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisciplineScoreCalculator {
    
    private final AnalyticsRiskProperties properties;
    
    public int calculate(int assignmentsLateCount, int missedDeadlinesCount) {
        int score = 100
                - (assignmentsLateCount * properties.getLateSubmissionPenalty())
                - (missedDeadlinesCount * properties.getMissedDeadlinePenalty());
        return Math.max(0, Math.min(score, 100));
    }
}

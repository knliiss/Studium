package dev.knalis.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.analytics.config.AnalyticsRiskProperties;
import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RawAcademicEvent;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PerformanceTrendCalculator {
    
    private static final String GRADE_ASSIGNED_EVENT = "GradeAssignedEventV1";
    private static final String TEST_COMPLETED_EVENT = "TestCompletedEventV1";
    private static final Set<String> SCORE_EVENT_TYPES = Set.of(GRADE_ASSIGNED_EVENT, TEST_COMPLETED_EVENT);
    
    private final RawAcademicEventRepository rawAcademicEventRepository;
    private final AnalyticsRiskProperties properties;
    private final ObjectMapper objectMapper;
    
    public PerformanceTrend calculate(UUID userId, UUID groupId) {
        List<RawAcademicEvent> scoreEvents = groupId == null
                ? rawAcademicEventRepository.findTop6ByUserIdAndEventTypeInOrderByOccurredAtDesc(userId, SCORE_EVENT_TYPES)
                : rawAcademicEventRepository.findTop6ByUserIdAndGroupIdAndEventTypeInOrderByOccurredAtDesc(
                        userId,
                        groupId,
                        SCORE_EVENT_TYPES
                );
        if (scoreEvents.size() < 4) {
            return PerformanceTrend.UNKNOWN;
        }
        
        Collections.reverse(scoreEvents);
        int midpoint = scoreEvents.size() / 2;
        double olderAverage = averageScore(scoreEvents.subList(0, midpoint));
        double recentAverage = averageScore(scoreEvents.subList(midpoint, scoreEvents.size()));
        double delta = recentAverage - olderAverage;
        
        if (delta >= properties.getPerformanceTrendDeltaThreshold()) {
            return PerformanceTrend.IMPROVING;
        }
        if (delta <= -properties.getPerformanceTrendDeltaThreshold()) {
            return PerformanceTrend.DECLINING;
        }
        return PerformanceTrend.STABLE;
    }
    
    private double averageScore(List<RawAcademicEvent> events) {
        return events.stream()
                .mapToDouble(this::extractNormalizedScore)
                .average()
                .orElse(0);
    }
    
    private double extractNormalizedScore(RawAcademicEvent rawAcademicEvent) {
        try {
            JsonNode payload = objectMapper.readTree(rawAcademicEvent.getPayloadJson());
            if (GRADE_ASSIGNED_EVENT.equals(rawAcademicEvent.getEventType())) {
                return payload.path("score").asDouble(0);
            }
            if (TEST_COMPLETED_EVENT.equals(rawAcademicEvent.getEventType())) {
                double score = payload.path("score").asDouble(0);
                double maxScore = payload.path("maxScore").asDouble(0);
                return maxScore > 0 ? (score / maxScore) * 100.0 : 0;
            }
            return 0;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate performance trend", exception);
        }
    }
}

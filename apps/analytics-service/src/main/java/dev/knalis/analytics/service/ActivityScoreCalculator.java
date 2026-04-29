package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.StudentProgressSnapshot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class ActivityScoreCalculator {
    
    public int calculate(StudentProgressSnapshot snapshot, Instant referenceTime) {
        int score = 0;
        score += recencyScore(snapshot.getLastActivityAt(), referenceTime);
        score += Math.min(15, snapshot.getLectureOpenCount() * 2);
        score += Math.min(15, snapshot.getTopicOpenCount() * 2);
        score += Math.min(10, snapshot.getAssignmentOpenedCount() * 3);
        score += Math.min(20, snapshot.getAssignmentsSubmittedCount() * 5);
        score += Math.min(10, snapshot.getTestStartedCount() * 3);
        score += Math.min(20, snapshot.getTestsCompletedCount() * 5);
        return Math.max(0, Math.min(score, 100));
    }
    
    private int recencyScore(Instant lastActivityAt, Instant referenceTime) {
        if (lastActivityAt == null) {
            return 0;
        }
        long inactivityDays = Math.max(0, Duration.between(lastActivityAt, referenceTime).toDays());
        if (inactivityDays <= 1) {
            return 30;
        }
        if (inactivityDays <= 3) {
            return 24;
        }
        if (inactivityDays <= 7) {
            return 16;
        }
        if (inactivityDays <= 14) {
            return 8;
        }
        return 0;
    }
}

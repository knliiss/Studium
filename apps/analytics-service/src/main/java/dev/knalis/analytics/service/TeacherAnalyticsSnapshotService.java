package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.TeacherAnalyticsSnapshot;
import dev.knalis.analytics.repository.TeacherAnalyticsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class TeacherAnalyticsSnapshotService {
    
    private final TeacherAnalyticsSnapshotRepository teacherAnalyticsSnapshotRepository;
    
    @Transactional
    public void recordAssignmentCreated(UUID teacherId) {
        updateSnapshot(teacherId, snapshot ->
                snapshot.setPublishedAssignmentsCount(snapshot.getPublishedAssignmentsCount() + 1));
    }
    
    @Transactional
    public void recordTestPublished(UUID teacherId) {
        updateSnapshot(teacherId, snapshot ->
                snapshot.setPublishedTestsCount(snapshot.getPublishedTestsCount() + 1));
    }
    
    @Transactional
    public void recordGradeAssigned(UUID teacherId, double score, Double reviewTimeHours) {
        updateSnapshot(teacherId, snapshot -> {
            snapshot.setAssignedGradesCount(snapshot.getAssignedGradesCount() + 1);
            snapshot.setScoreTotal(snapshot.getScoreTotal() + score);
            snapshot.setScoreCount(snapshot.getScoreCount() + 1);
            if (score < 60) {
                snapshot.setFailingScoreCount(snapshot.getFailingScoreCount() + 1);
            }
            if (reviewTimeHours != null) {
                snapshot.setReviewTimeHoursTotal(snapshot.getReviewTimeHoursTotal() + reviewTimeHours);
                snapshot.setReviewTimeSampleCount(snapshot.getReviewTimeSampleCount() + 1);
            }
        });
    }
    
    @Transactional
    public void recordTestCompleted(UUID teacherId, double normalizedScore) {
        updateSnapshot(teacherId, snapshot -> {
            snapshot.setScoreTotal(snapshot.getScoreTotal() + normalizedScore);
            snapshot.setScoreCount(snapshot.getScoreCount() + 1);
            if (normalizedScore < 60) {
                snapshot.setFailingScoreCount(snapshot.getFailingScoreCount() + 1);
            }
        });
    }
    
    private void updateSnapshot(UUID teacherId, Consumer<TeacherAnalyticsSnapshot> mutation) {
        if (teacherId == null) {
            return;
        }
        TeacherAnalyticsSnapshot snapshot = teacherAnalyticsSnapshotRepository.findByTeacherId(teacherId)
                .orElseGet(() -> newSnapshot(teacherId));
        mutation.accept(snapshot);
        refreshDerivedMetrics(snapshot);
        teacherAnalyticsSnapshotRepository.save(snapshot);
    }
    
    private TeacherAnalyticsSnapshot newSnapshot(UUID teacherId) {
        TeacherAnalyticsSnapshot snapshot = new TeacherAnalyticsSnapshot();
        snapshot.setTeacherId(teacherId);
        return snapshot;
    }
    
    private void refreshDerivedMetrics(TeacherAnalyticsSnapshot snapshot) {
        snapshot.setAverageStudentScore(snapshot.getScoreCount() > 0 ? round(snapshot.getScoreTotal() / snapshot.getScoreCount()) : null);
        snapshot.setFailingRate(snapshot.getScoreCount() > 0
                ? round((snapshot.getFailingScoreCount() * 100.0) / snapshot.getScoreCount())
                : 0);
        snapshot.setAverageReviewTimeHours(snapshot.getReviewTimeSampleCount() > 0
                ? round(snapshot.getReviewTimeHoursTotal() / snapshot.getReviewTimeSampleCount())
                : null);
    }
    
    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

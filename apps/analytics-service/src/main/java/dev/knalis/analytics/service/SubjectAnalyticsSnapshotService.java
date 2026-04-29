package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.SubjectAnalyticsSnapshot;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import dev.knalis.analytics.repository.StudentProgressSnapshotRepository;
import dev.knalis.analytics.repository.SubjectAnalyticsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SubjectAnalyticsSnapshotService {
    
    private final SubjectAnalyticsSnapshotRepository subjectAnalyticsSnapshotRepository;
    private final RawAcademicEventRepository rawAcademicEventRepository;
    private final StudentProgressSnapshotRepository studentProgressSnapshotRepository;
    
    @Transactional
    public void recordLectureOpened(UUID subjectId, UUID groupId) {
        updateSnapshot(subjectId, groupId, snapshot ->
                snapshot.setLectureOpenCount(snapshot.getLectureOpenCount() + 1));
    }
    
    @Transactional
    public void recordAssignmentOpened(UUID subjectId, UUID groupId) {
        updateSnapshot(subjectId, groupId, snapshot ->
                snapshot.setAssignmentOpenedCount(snapshot.getAssignmentOpenedCount() + 1));
    }
    
    @Transactional
    public void recordAssignmentCreated(UUID subjectId, UUID groupId) {
        updateSnapshot(subjectId, groupId, snapshot ->
                snapshot.setCompletionRate(snapshot.getCompletionRate()));
    }
    
    @Transactional
    public void recordAssignmentSubmitted(UUID subjectId, UUID groupId, boolean wasLate) {
        updateSnapshot(subjectId, groupId, snapshot -> {
            snapshot.setAssignmentsSubmittedCountValue(snapshot.getAssignmentsSubmittedCountValue() + 1);
            if (wasLate) {
                snapshot.setLateSubmissionCountValue(snapshot.getLateSubmissionCountValue() + 1);
            }
        });
    }
    
    @Transactional
    public void recordDeadlineMissed(UUID subjectId, UUID groupId) {
        updateSnapshot(subjectId, groupId, snapshot ->
                snapshot.setMissedDeadlineCountValue(snapshot.getMissedDeadlineCountValue() + 1));
    }
    
    @Transactional
    public void recordTestStarted(UUID subjectId, UUID groupId) {
        updateSnapshot(subjectId, groupId, snapshot ->
                snapshot.setTestStartedCount(snapshot.getTestStartedCount() + 1));
    }
    
    @Transactional
    public void recordTestCompleted(UUID subjectId, UUID groupId, double normalizedScore) {
        updateSnapshot(subjectId, groupId, snapshot -> {
            snapshot.setTestCompletionCount(snapshot.getTestCompletionCount() + 1);
            snapshot.setScoreTotal(snapshot.getScoreTotal() + normalizedScore);
            snapshot.setScoreCount(snapshot.getScoreCount() + 1);
        });
    }
    
    @Transactional
    public void recordGradeAssigned(UUID subjectId, UUID groupId, double score) {
        updateSnapshot(subjectId, groupId, snapshot -> {
            snapshot.setScoreTotal(snapshot.getScoreTotal() + score);
            snapshot.setScoreCount(snapshot.getScoreCount() + 1);
        });
    }
    
    @Transactional
    public void recordTestPublished(UUID subjectId, UUID groupId) {
        updateSnapshot(subjectId, groupId, snapshot ->
                snapshot.setCompletionRate(snapshot.getCompletionRate()));
    }
    
    private void updateSnapshot(UUID subjectId, UUID groupId, Consumer<SubjectAnalyticsSnapshot> mutation) {
        if (subjectId == null) {
            return;
        }
        SubjectAnalyticsSnapshot snapshot = groupId == null
                ? subjectAnalyticsSnapshotRepository.findBySubjectIdAndGroupIdIsNull(subjectId)
                .orElseGet(() -> newSnapshot(subjectId, null))
                : subjectAnalyticsSnapshotRepository.findBySubjectIdAndGroupId(subjectId, groupId)
                .orElseGet(() -> newSnapshot(subjectId, groupId));
        mutation.accept(snapshot);
        refreshDerivedMetrics(snapshot);
        subjectAnalyticsSnapshotRepository.save(snapshot);
    }
    
    private SubjectAnalyticsSnapshot newSnapshot(UUID subjectId, UUID groupId) {
        SubjectAnalyticsSnapshot snapshot = new SubjectAnalyticsSnapshot();
        snapshot.setSubjectId(subjectId);
        snapshot.setGroupId(groupId);
        return snapshot;
    }
    
    private void refreshDerivedMetrics(SubjectAnalyticsSnapshot snapshot) {
        snapshot.setAverageScore(snapshot.getScoreCount() > 0 ? round(snapshot.getScoreTotal() / snapshot.getScoreCount()) : null);
        int startedCount = snapshot.getAssignmentOpenedCount() + snapshot.getTestStartedCount();
        int completedCount = snapshot.getAssignmentsSubmittedCountValue() + snapshot.getTestCompletionCount();
        snapshot.setCompletionRate(calculateRate(completedCount, startedCount));
        snapshot.setLateSubmissionRate(
                calculateRate(snapshot.getLateSubmissionCountValue(), snapshot.getAssignmentsSubmittedCountValue())
        );
        snapshot.setMissedDeadlineRate(
                calculateRate(
                        snapshot.getMissedDeadlineCountValue(),
                        snapshot.getAssignmentsSubmittedCountValue() + snapshot.getMissedDeadlineCountValue()
                )
        );
        snapshot.setActiveStudentsCount(
                rawAcademicEventRepository.countDistinctUsersBySubjectIdAndGroupId(snapshot.getSubjectId(), snapshot.getGroupId())
        );
        snapshot.setAtRiskStudentsCount(resolveAtRiskStudentsCount(snapshot.getGroupId()));
        snapshot.setUpdatedAt(Instant.now());
    }
    
    private long resolveAtRiskStudentsCount(UUID groupId) {
        if (groupId == null) {
            return studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevelNot(RiskLevel.LOW);
        }
        return studentProgressSnapshotRepository.countByGroupIdAndRiskLevelNot(groupId, RiskLevel.LOW);
    }
    
    private double calculateRate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return round(Math.min((numerator * 100.0) / denominator, 100.0));
    }
    
    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

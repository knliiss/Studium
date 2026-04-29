package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import dev.knalis.analytics.repository.StudentProgressSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class StudentProgressSnapshotService {
    
    private final StudentProgressSnapshotRepository studentProgressSnapshotRepository;
    private final ActivityScoreCalculator activityScoreCalculator;
    private final DisciplineScoreCalculator disciplineScoreCalculator;
    private final PerformanceTrendCalculator performanceTrendCalculator;
    private final RiskCalculator riskCalculator;
    
    @Transactional
    public void recordLectureOpened(UUID userId, UUID groupId, Instant occurredAt) {
        updateSnapshots(userId, groupId, false, snapshot -> {
            snapshot.setLectureOpenCount(snapshot.getLectureOpenCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordTopicOpened(UUID userId, UUID groupId, Instant occurredAt) {
        updateSnapshots(userId, groupId, false, snapshot -> {
            snapshot.setTopicOpenCount(snapshot.getTopicOpenCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordAssignmentOpened(UUID userId, UUID groupId, Instant occurredAt) {
        updateSnapshots(userId, groupId, false, snapshot -> {
            snapshot.setAssignmentOpenedCount(snapshot.getAssignmentOpenedCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordAssignmentCreated(UUID userId, UUID groupId) {
        updateSnapshots(userId, groupId, false, snapshot ->
                snapshot.setAssignmentsCreatedCount(snapshot.getAssignmentsCreatedCount() + 1));
    }
    
    @Transactional
    public void recordAssignmentSubmitted(UUID userId, UUID groupId, boolean wasLate, Instant occurredAt) {
        updateSnapshots(userId, groupId, false, snapshot -> {
            snapshot.setAssignmentsSubmittedCount(snapshot.getAssignmentsSubmittedCount() + 1);
            if (wasLate) {
                snapshot.setAssignmentsLateCount(snapshot.getAssignmentsLateCount() + 1);
            }
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordTestStarted(UUID userId, UUID groupId, Instant occurredAt) {
        updateSnapshots(userId, groupId, false, snapshot -> {
            snapshot.setTestStartedCount(snapshot.getTestStartedCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordTestCompleted(UUID userId, UUID groupId, double normalizedScore, Instant occurredAt) {
        updateSnapshots(userId, groupId, true, snapshot -> {
            snapshot.setTestsCompletedCount(snapshot.getTestsCompletedCount() + 1);
            snapshot.setScoreTotal(snapshot.getScoreTotal() + normalizedScore);
            snapshot.setScoreCount(snapshot.getScoreCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordDeadlineMissed(UUID userId, UUID groupId, Instant occurredAt) {
        updateSnapshots(userId, groupId, false, snapshot -> {
            snapshot.setMissedDeadlinesCount(snapshot.getMissedDeadlinesCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    @Transactional
    public void recordGradeAssigned(UUID userId, UUID groupId, double score, Instant occurredAt) {
        updateSnapshots(userId, groupId, true, snapshot -> {
            snapshot.setScoreTotal(snapshot.getScoreTotal() + score);
            snapshot.setScoreCount(snapshot.getScoreCount() + 1);
            touchLastActivity(snapshot, occurredAt);
        });
    }
    
    private void updateSnapshots(UUID userId, UUID groupId, boolean recalculateTrend, Consumer<StudentProgressSnapshot> mutation) {
        for (StudentProgressSnapshot snapshot : getTargetSnapshots(userId, groupId)) {
            mutation.accept(snapshot);
            refreshDerivedMetrics(snapshot, recalculateTrend);
            studentProgressSnapshotRepository.save(snapshot);
        }
    }
    
    private List<StudentProgressSnapshot> getTargetSnapshots(UUID userId, UUID groupId) {
        List<StudentProgressSnapshot> snapshots = new ArrayList<>();
        snapshots.add(studentProgressSnapshotRepository.findByUserIdAndGroupIdIsNull(userId)
                .orElseGet(() -> newSnapshot(userId, null)));
        if (groupId != null) {
            snapshots.add(studentProgressSnapshotRepository.findByUserIdAndGroupId(userId, groupId)
                    .orElseGet(() -> newSnapshot(userId, groupId)));
        }
        return snapshots;
    }
    
    private StudentProgressSnapshot newSnapshot(UUID userId, UUID groupId) {
        StudentProgressSnapshot snapshot = new StudentProgressSnapshot();
        snapshot.setUserId(userId);
        snapshot.setGroupId(groupId);
        snapshot.setRiskLevel(RiskLevel.LOW);
        snapshot.setPerformanceTrend(PerformanceTrend.UNKNOWN);
        snapshot.setDisciplineScore(100);
        return snapshot;
    }
    
    private void refreshDerivedMetrics(StudentProgressSnapshot snapshot, boolean recalculateTrend) {
        snapshot.setAverageScore(snapshot.getScoreCount() > 0 ? round(snapshot.getScoreTotal() / snapshot.getScoreCount()) : null);
        snapshot.setActivityScore(activityScoreCalculator.calculate(snapshot, Instant.now()));
        snapshot.setDisciplineScore(
                disciplineScoreCalculator.calculate(
                        snapshot.getAssignmentsLateCount(),
                        snapshot.getMissedDeadlinesCount()
                )
        );
        if (recalculateTrend) {
            snapshot.setPerformanceTrend(performanceTrendCalculator.calculate(snapshot.getUserId(), snapshot.getGroupId()));
        }
        snapshot.setRiskLevel(riskCalculator.calculate(snapshot, Instant.now()));
    }
    
    private void touchLastActivity(StudentProgressSnapshot snapshot, Instant occurredAt) {
        if (occurredAt != null && (snapshot.getLastActivityAt() == null || occurredAt.isAfter(snapshot.getLastActivityAt()))) {
            snapshot.setLastActivityAt(occurredAt);
        }
    }
    
    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

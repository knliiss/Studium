package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import dev.knalis.analytics.repository.StudentProgressSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProgressSnapshotServiceTest {

    @Mock
    private StudentProgressSnapshotRepository studentProgressSnapshotRepository;

    @Mock
    private ActivityScoreCalculator activityScoreCalculator;

    @Mock
    private DisciplineScoreCalculator disciplineScoreCalculator;

    @Mock
    private PerformanceTrendCalculator performanceTrendCalculator;

    @Mock
    private RiskCalculator riskCalculator;

    private StudentProgressSnapshotService studentProgressSnapshotService;

    @BeforeEach
    void setUp() {
        studentProgressSnapshotService = new StudentProgressSnapshotService(
                studentProgressSnapshotRepository,
                activityScoreCalculator,
                disciplineScoreCalculator,
                performanceTrendCalculator,
                riskCalculator
        );
    }

    @Test
    void recordLectureOpenedUsesUserAndGroupSnapshotsWithoutDuplicateCreationRace() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        StudentProgressSnapshot userSnapshot = baseSnapshot(userId, null);
        StudentProgressSnapshot groupSnapshot = baseSnapshot(userId, groupId);

        when(studentProgressSnapshotRepository.findFirstByUserIdAndGroupIdIsNullOrderByUpdatedAtDesc(userId))
                .thenReturn(Optional.of(userSnapshot));
        when(studentProgressSnapshotRepository.findFirstByUserIdAndGroupIdOrderByUpdatedAtDesc(userId, groupId))
                .thenReturn(Optional.of(groupSnapshot));
        when(activityScoreCalculator.calculate(any(StudentProgressSnapshot.class), any(Instant.class))).thenReturn(25);
        when(disciplineScoreCalculator.calculate(anyInt(), anyInt())).thenReturn(95);
        when(riskCalculator.calculate(any(StudentProgressSnapshot.class), any(Instant.class))).thenReturn(RiskLevel.MEDIUM);

        studentProgressSnapshotService.recordLectureOpened(userId, groupId, occurredAt);

        verify(studentProgressSnapshotRepository).insertUserSnapshotIfAbsent(any(UUID.class), eq(userId));
        verify(studentProgressSnapshotRepository).insertGroupSnapshotIfAbsent(any(UUID.class), eq(userId), eq(groupId));
        verify(studentProgressSnapshotRepository, times(2)).save(any(StudentProgressSnapshot.class));
        verify(performanceTrendCalculator, never()).calculate(any(UUID.class), any(UUID.class));

        assertEquals(1, userSnapshot.getLectureOpenCount());
        assertEquals(1, groupSnapshot.getLectureOpenCount());
        assertEquals(occurredAt, userSnapshot.getLastActivityAt());
        assertEquals(occurredAt, groupSnapshot.getLastActivityAt());
        assertEquals(25, userSnapshot.getActivityScore());
        assertEquals(25, groupSnapshot.getActivityScore());
        assertEquals(95, userSnapshot.getDisciplineScore());
        assertEquals(95, groupSnapshot.getDisciplineScore());
        assertEquals(RiskLevel.MEDIUM, userSnapshot.getRiskLevel());
        assertEquals(RiskLevel.MEDIUM, groupSnapshot.getRiskLevel());
    }

    private StudentProgressSnapshot baseSnapshot(UUID userId, UUID groupId) {
        StudentProgressSnapshot snapshot = new StudentProgressSnapshot();
        snapshot.setUserId(userId);
        snapshot.setGroupId(groupId);
        snapshot.setRiskLevel(RiskLevel.LOW);
        snapshot.setPerformanceTrend(PerformanceTrend.UNKNOWN);
        snapshot.setDisciplineScore(100);
        snapshot.setActivityScore(0);
        snapshot.setScoreTotal(0);
        snapshot.setScoreCount(0);
        snapshot.setAssignmentsLateCount(0);
        snapshot.setMissedDeadlinesCount(0);
        return snapshot;
    }
}

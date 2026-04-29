package dev.knalis.analytics.service;

import dev.knalis.analytics.dto.response.DashboardOverviewResponse;
import dev.knalis.analytics.dto.response.GroupOverviewResponse;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import dev.knalis.analytics.mapper.AnalyticsReadMapper;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import dev.knalis.analytics.repository.StudentProgressSnapshotRepository;
import dev.knalis.analytics.repository.SubjectAnalyticsSnapshotRepository;
import dev.knalis.analytics.repository.TeacherAnalyticsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsReadServiceTest {
    
    @Mock
    private StudentProgressSnapshotRepository studentProgressSnapshotRepository;
    
    @Mock
    private SubjectAnalyticsSnapshotRepository subjectAnalyticsSnapshotRepository;
    
    @Mock
    private TeacherAnalyticsSnapshotRepository teacherAnalyticsSnapshotRepository;

    @Mock
    private RawAcademicEventRepository rawAcademicEventRepository;
    
    private AnalyticsReadService analyticsReadService;
    
    @BeforeEach
    void setUp() {
        analyticsReadService = new AnalyticsReadService(
                studentProgressSnapshotRepository,
                subjectAnalyticsSnapshotRepository,
                teacherAnalyticsSnapshotRepository,
                rawAcademicEventRepository,
                new AnalyticsReadMapper()
        );
    }
    
    @Test
    void getDashboardOverviewAggregatesUserLevelSnapshots() {
        StudentProgressSnapshot lowRisk = new StudentProgressSnapshot();
        lowRisk.setRiskLevel(RiskLevel.LOW);
        lowRisk.setAverageScore(85.0);
        lowRisk.setDisciplineScore(90);
        lowRisk.setActivityScore(80);
        lowRisk.setMissedDeadlinesCount(1);
        lowRisk.setAssignmentsLateCount(1);
        
        StudentProgressSnapshot highRisk = new StudentProgressSnapshot();
        highRisk.setRiskLevel(RiskLevel.HIGH);
        highRisk.setAverageScore(55.0);
        highRisk.setDisciplineScore(60);
        highRisk.setActivityScore(40);
        highRisk.setMissedDeadlinesCount(3);
        highRisk.setAssignmentsLateCount(2);
        
        when(studentProgressSnapshotRepository.findAllByGroupIdIsNullOrderByUpdatedAtDesc())
                .thenReturn(List.of(lowRisk, highRisk));
        when(studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevel(RiskLevel.LOW)).thenReturn(1L);
        when(studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevel(RiskLevel.MEDIUM)).thenReturn(0L);
        when(studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevel(RiskLevel.HIGH)).thenReturn(1L);
        
        DashboardOverviewResponse response = analyticsReadService.getDashboardOverview();
        
        assertEquals(2, response.totalStudentsTracked());
        assertEquals(1, response.highRiskStudentsCount());
        assertEquals(70.0, response.averagePlatformScore());
        assertEquals(4, response.totalMissedDeadlines());
    }
    
    @Test
    void getGroupOverviewAggregatesGroupSnapshots() {
        UUID groupId = UUID.randomUUID();
        StudentProgressSnapshot mediumRisk = new StudentProgressSnapshot();
        mediumRisk.setGroupId(groupId);
        mediumRisk.setRiskLevel(RiskLevel.MEDIUM);
        mediumRisk.setAverageScore(70.0);
        mediumRisk.setActivityScore(60);
        mediumRisk.setDisciplineScore(80);
        mediumRisk.setMissedDeadlinesCount(1);
        mediumRisk.setAssignmentsLateCount(1);
        mediumRisk.setUpdatedAt(Instant.now());
        
        StudentProgressSnapshot highRisk = new StudentProgressSnapshot();
        highRisk.setGroupId(groupId);
        highRisk.setRiskLevel(RiskLevel.HIGH);
        highRisk.setAverageScore(50.0);
        highRisk.setActivityScore(40);
        highRisk.setDisciplineScore(50);
        highRisk.setMissedDeadlinesCount(3);
        highRisk.setAssignmentsLateCount(2);
        highRisk.setUpdatedAt(Instant.now());
        
        when(studentProgressSnapshotRepository.findAllByGroupIdOrderByUpdatedAtDesc(groupId))
                .thenReturn(List.of(mediumRisk, highRisk));
        
        GroupOverviewResponse response = analyticsReadService.getGroupOverview(groupId);
        
        assertEquals(2, response.totalStudentsTracked());
        assertEquals(1, response.mediumRiskStudentsCount());
        assertEquals(1, response.highRiskStudentsCount());
        assertEquals(4, response.totalMissedDeadlines());
    }
}

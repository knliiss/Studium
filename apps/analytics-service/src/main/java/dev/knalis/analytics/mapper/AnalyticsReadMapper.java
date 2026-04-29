package dev.knalis.analytics.mapper;

import dev.knalis.analytics.dto.response.GroupOverviewResponse;
import dev.knalis.analytics.dto.response.StudentAnalyticsResponse;
import dev.knalis.analytics.dto.response.StudentGroupProgressResponse;
import dev.knalis.analytics.dto.response.StudentRiskResponse;
import dev.knalis.analytics.dto.response.SubjectAnalyticsResponse;
import dev.knalis.analytics.dto.response.TeacherAnalyticsResponse;
import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import dev.knalis.analytics.entity.SubjectAnalyticsSnapshot;
import dev.knalis.analytics.entity.TeacherAnalyticsSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class AnalyticsReadMapper {
    
    public StudentAnalyticsResponse toStudentResponse(
            UUID userId,
            StudentProgressSnapshot snapshot,
            List<StudentGroupProgressResponse> groupProgress
    ) {
        return new StudentAnalyticsResponse(
                userId,
                snapshot != null ? snapshot.getAverageScore() : null,
                snapshot != null ? snapshot.getAssignmentsCreatedCount() : 0,
                snapshot != null ? snapshot.getAssignmentsSubmittedCount() : 0,
                snapshot != null ? snapshot.getAssignmentsLateCount() : 0,
                snapshot != null ? snapshot.getTestsCompletedCount() : 0,
                snapshot != null ? snapshot.getMissedDeadlinesCount() : 0,
                snapshot != null ? snapshot.getLectureOpenCount() : 0,
                snapshot != null ? snapshot.getTopicOpenCount() : 0,
                snapshot != null ? snapshot.getLastActivityAt() : null,
                snapshot != null ? snapshot.getActivityScore() : 0,
                snapshot != null ? snapshot.getDisciplineScore() : 100,
                snapshot != null ? snapshot.getRiskLevel() : RiskLevel.LOW,
                snapshot != null ? snapshot.getPerformanceTrend() : PerformanceTrend.UNKNOWN,
                snapshot != null ? snapshot.getUpdatedAt() : null,
                groupProgress
        );
    }
    
    public StudentGroupProgressResponse toStudentGroupProgressResponse(StudentProgressSnapshot snapshot) {
        return new StudentGroupProgressResponse(
                snapshot.getGroupId(),
                snapshot.getAverageScore(),
                snapshot.getAssignmentsCreatedCount(),
                snapshot.getAssignmentsSubmittedCount(),
                snapshot.getAssignmentsLateCount(),
                snapshot.getTestsCompletedCount(),
                snapshot.getMissedDeadlinesCount(),
                snapshot.getLectureOpenCount(),
                snapshot.getTopicOpenCount(),
                snapshot.getLastActivityAt(),
                snapshot.getActivityScore(),
                snapshot.getDisciplineScore(),
                snapshot.getRiskLevel(),
                snapshot.getPerformanceTrend(),
                snapshot.getUpdatedAt()
        );
    }
    
    public StudentRiskResponse toStudentRiskResponse(UUID userId, StudentProgressSnapshot snapshot) {
        return new StudentRiskResponse(
                userId,
                snapshot != null ? snapshot.getRiskLevel() : RiskLevel.LOW,
                snapshot != null ? snapshot.getPerformanceTrend() : PerformanceTrend.UNKNOWN,
                snapshot != null ? snapshot.getAverageScore() : null,
                snapshot != null ? snapshot.getActivityScore() : 0,
                snapshot != null ? snapshot.getDisciplineScore() : 100,
                snapshot != null ? snapshot.getMissedDeadlinesCount() : 0,
                snapshot != null ? snapshot.getLastActivityAt() : null,
                snapshot != null ? snapshot.getUpdatedAt() : null
        );
    }
    
    public SubjectAnalyticsResponse toSubjectResponse(SubjectAnalyticsSnapshot snapshot) {
        return new SubjectAnalyticsResponse(
                snapshot.getSubjectId(),
                snapshot.getGroupId(),
                snapshot.getAverageScore(),
                snapshot.getCompletionRate(),
                snapshot.getLateSubmissionRate(),
                snapshot.getMissedDeadlineRate(),
                snapshot.getActiveStudentsCount(),
                snapshot.getAtRiskStudentsCount(),
                snapshot.getLectureOpenCount(),
                snapshot.getTestCompletionCount(),
                snapshot.getUpdatedAt()
        );
    }
    
    public TeacherAnalyticsResponse toTeacherResponse(UUID teacherId, TeacherAnalyticsSnapshot snapshot) {
        return new TeacherAnalyticsResponse(
                teacherId,
                snapshot != null ? snapshot.getPublishedAssignmentsCount() : 0,
                snapshot != null ? snapshot.getPublishedTestsCount() : 0,
                snapshot != null ? snapshot.getAssignedGradesCount() : 0,
                snapshot != null ? snapshot.getAverageReviewTimeHours() : null,
                snapshot != null ? snapshot.getAverageStudentScore() : null,
                snapshot != null ? snapshot.getFailingRate() : 0,
                snapshot != null ? snapshot.getUpdatedAt() : null
        );
    }
    
    public GroupOverviewResponse toGroupOverviewResponse(
            UUID groupId,
            long totalStudentsTracked,
            long lowRiskStudentsCount,
            long mediumRiskStudentsCount,
            long highRiskStudentsCount,
            Double averageScore,
            double averageActivityScore,
            double averageDisciplineScore,
            long totalMissedDeadlines,
            long totalLateSubmissions,
            Instant updatedAt
    ) {
        return new GroupOverviewResponse(
                groupId,
                totalStudentsTracked,
                lowRiskStudentsCount,
                mediumRiskStudentsCount,
                highRiskStudentsCount,
                averageScore,
                averageActivityScore,
                averageDisciplineScore,
                totalMissedDeadlines,
                totalLateSubmissions,
                updatedAt
        );
    }
}

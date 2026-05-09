package dev.knalis.analytics.service;

import dev.knalis.analytics.dto.response.DashboardOverviewResponse;
import dev.knalis.analytics.dto.response.GroupOverviewResponse;
import dev.knalis.analytics.dto.response.StudentAnalyticsResponse;
import dev.knalis.analytics.dto.response.StudentGroupProgressPageResponse;
import dev.knalis.analytics.dto.response.StudentGroupProgressResponse;
import dev.knalis.analytics.dto.response.StudentRiskResponse;
import dev.knalis.analytics.dto.response.SubjectAnalyticsPageResponse;
import dev.knalis.analytics.dto.response.SubjectAnalyticsResponse;
import dev.knalis.analytics.dto.response.TeacherAnalyticsResponse;
import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import dev.knalis.analytics.entity.SubjectAnalyticsSnapshot;
import dev.knalis.analytics.mapper.AnalyticsReadMapper;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import dev.knalis.analytics.repository.StudentProgressSnapshotRepository;
import dev.knalis.analytics.repository.SubjectAnalyticsSnapshotRepository;
import dev.knalis.analytics.repository.TeacherAnalyticsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsReadService {

    private static final Set<String> GROUP_STUDENT_SORT_FIELDS = Set.of(
            "updatedAt",
            "averageScore",
            "lastActivityAt",
            "riskLevel"
    );
    private static final Set<String> SUBJECT_ANALYTICS_SORT_FIELDS = Set.of(
            "updatedAt",
            "averageScore",
            "atRiskStudentsCount",
            "groupId"
    );
    
    private final StudentProgressSnapshotRepository studentProgressSnapshotRepository;
    private final SubjectAnalyticsSnapshotRepository subjectAnalyticsSnapshotRepository;
    private final TeacherAnalyticsSnapshotRepository teacherAnalyticsSnapshotRepository;
    private final RawAcademicEventRepository rawAcademicEventRepository;
    private final AnalyticsReadMapper analyticsReadMapper;
    
    @Transactional(readOnly = true)
    public StudentAnalyticsResponse getStudentAnalytics(UUID userId) {
        StudentProgressSnapshot userSnapshot = studentProgressSnapshotRepository
                .findFirstByUserIdAndGroupIdIsNullOrderByUpdatedAtDesc(userId)
                .orElse(null);
        List<StudentGroupProgressResponse> groupProgress = studentProgressSnapshotRepository
                .findAllByUserIdAndGroupIdIsNotNullOrderByUpdatedAtDesc(userId)
                .stream()
                .map(analyticsReadMapper::toStudentGroupProgressResponse)
                .toList();
        return analyticsReadMapper.toStudentResponse(userId, userSnapshot, groupProgress);
    }
    
    @Transactional(readOnly = true)
    public List<SubjectAnalyticsResponse> getStudentSubjects(UUID userId) {
        List<UUID> groupIds = studentProgressSnapshotRepository.findAllByUserIdAndGroupIdIsNotNullOrderByUpdatedAtDesc(userId)
                .stream()
                .map(StudentProgressSnapshot::getGroupId)
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return subjectAnalyticsSnapshotRepository.findAllByGroupIdInOrderByUpdatedAtDesc(groupIds).stream()
                .map(analyticsReadMapper::toSubjectResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public StudentRiskResponse getStudentRisk(UUID userId) {
        return analyticsReadMapper.toStudentRiskResponse(
                userId,
                studentProgressSnapshotRepository.findFirstByUserIdAndGroupIdIsNullOrderByUpdatedAtDesc(userId).orElse(null)
        );
    }
    
    @Transactional(readOnly = true)
    public GroupOverviewResponse getGroupOverview(UUID groupId) {
        List<StudentProgressSnapshot> snapshots = studentProgressSnapshotRepository.findAllByGroupIdOrderByUpdatedAtDesc(groupId);
        Double averageScore = round(snapshots.stream()
                .filter(snapshot -> snapshot.getAverageScore() != null)
                .mapToDouble(StudentProgressSnapshot::getAverageScore)
                .average()
                .orElse(0));
        double averageActivityScore = round(snapshots.stream()
                .mapToInt(StudentProgressSnapshot::getActivityScore)
                .average()
                .orElse(0));
        double averageDisciplineScore = round(snapshots.stream()
                .mapToInt(StudentProgressSnapshot::getDisciplineScore)
                .average()
                .orElse(0));
        long totalMissedDeadlines = snapshots.stream().mapToLong(StudentProgressSnapshot::getMissedDeadlinesCount).sum();
        long totalLateSubmissions = snapshots.stream().mapToLong(StudentProgressSnapshot::getAssignmentsLateCount).sum();
        Instant updatedAt = snapshots.stream()
                .map(StudentProgressSnapshot::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        
        return analyticsReadMapper.toGroupOverviewResponse(
                groupId,
                snapshots.size(),
                snapshots.stream().filter(snapshot -> snapshot.getRiskLevel() == RiskLevel.LOW).count(),
                snapshots.stream().filter(snapshot -> snapshot.getRiskLevel() == RiskLevel.MEDIUM).count(),
                snapshots.stream().filter(snapshot -> snapshot.getRiskLevel() == RiskLevel.HIGH).count(),
                snapshots.isEmpty() ? null : averageScore,
                averageActivityScore,
                averageDisciplineScore,
                totalMissedDeadlines,
                totalLateSubmissions,
                updatedAt
        );
    }
    
    @Transactional(readOnly = true)
    public StudentGroupProgressPageResponse getGroupStudents(
            UUID groupId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy, GROUP_STUDENT_SORT_FIELDS, "updatedAt"))
        );
        Page<StudentProgressSnapshot> snapshotPage = studentProgressSnapshotRepository.findAllByGroupId(groupId, pageRequest);
        return new StudentGroupProgressPageResponse(
                snapshotPage.getContent().stream().map(analyticsReadMapper::toStudentGroupProgressResponse).toList(),
                snapshotPage.getNumber(),
                snapshotPage.getSize(),
                snapshotPage.getTotalElements(),
                snapshotPage.getTotalPages(),
                snapshotPage.isFirst(),
                snapshotPage.isLast()
        );
    }
    
    @Transactional(readOnly = true)
    public SubjectAnalyticsPageResponse getSubjectAnalytics(
            UUID subjectId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy, SUBJECT_ANALYTICS_SORT_FIELDS, "updatedAt"))
        );
        Page<SubjectAnalyticsSnapshot> snapshotPage = subjectAnalyticsSnapshotRepository.findAllBySubjectId(subjectId, pageRequest);
        return new SubjectAnalyticsPageResponse(
                snapshotPage.getContent().stream().map(analyticsReadMapper::toSubjectResponse).toList(),
                snapshotPage.getNumber(),
                snapshotPage.getSize(),
                snapshotPage.getTotalElements(),
                snapshotPage.getTotalPages(),
                snapshotPage.isFirst(),
                snapshotPage.isLast()
        );
    }
    
    @Transactional(readOnly = true)
    public SubjectAnalyticsResponse getSubjectAnalytics(UUID subjectId, UUID groupId) {
        return subjectAnalyticsSnapshotRepository.findBySubjectIdAndGroupId(subjectId, groupId)
                .map(analyticsReadMapper::toSubjectResponse)
                .orElse(new SubjectAnalyticsResponse(subjectId, groupId, null, 0, 0, 0, 0, 0, 0, 0, null));
    }
    
    @Transactional(readOnly = true)
    public TeacherAnalyticsResponse getTeacherAnalytics(UUID teacherId) {
        return analyticsReadMapper.toTeacherResponse(
                teacherId,
                teacherAnalyticsSnapshotRepository.findByTeacherId(teacherId).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public List<GroupOverviewResponse> getTeacherGroupsAtRisk(UUID teacherId) {
        return rawAcademicEventRepository.findDistinctGroupIdsByTeacherId(teacherId).stream()
                .map(this::getGroupOverview)
                .filter(this::isAtRisk)
                .sorted(Comparator
                        .comparing(GroupOverviewResponse::highRiskStudentsCount).reversed()
                        .thenComparing(GroupOverviewResponse::mediumRiskStudentsCount, Comparator.reverseOrder())
                        .thenComparing(GroupOverviewResponse::totalMissedDeadlines, Comparator.reverseOrder())
                        .thenComparing(GroupOverviewResponse::totalLateSubmissions, Comparator.reverseOrder()))
                .limit(10)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getDashboardOverview() {
        List<StudentProgressSnapshot> snapshots = studentProgressSnapshotRepository.findAllByGroupIdIsNullOrderByUpdatedAtDesc();
        long totalStudentsTracked = snapshots.size();
        return new DashboardOverviewResponse(
                totalStudentsTracked,
                studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevel(RiskLevel.LOW),
                studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevel(RiskLevel.MEDIUM),
                studentProgressSnapshotRepository.countByGroupIdIsNullAndRiskLevel(RiskLevel.HIGH),
                round(snapshots.stream()
                        .filter(snapshot -> snapshot.getAverageScore() != null)
                        .mapToDouble(StudentProgressSnapshot::getAverageScore)
                        .average()
                        .orElse(0)),
                round(snapshots.stream()
                        .mapToInt(StudentProgressSnapshot::getDisciplineScore)
                        .average()
                        .orElse(0)),
                round(snapshots.stream()
                        .mapToInt(StudentProgressSnapshot::getActivityScore)
                        .average()
                        .orElse(0)),
                snapshots.stream().mapToLong(StudentProgressSnapshot::getMissedDeadlinesCount).sum(),
                snapshots.stream().mapToLong(StudentProgressSnapshot::getAssignmentsLateCount).sum()
        );
    }
    
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String resolveSortField(String sortBy, Set<String> allowedFields, String fallback) {
        if (sortBy == null || sortBy.isBlank()) {
            return fallback;
        }
        return allowedFields.contains(sortBy) ? sortBy : fallback;
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private boolean isAtRisk(GroupOverviewResponse overview) {
        return overview.highRiskStudentsCount() > 0
                || overview.mediumRiskStudentsCount() > 0
                || overview.totalMissedDeadlines() > 0
                || overview.totalLateSubmissions() > 0;
    }
}

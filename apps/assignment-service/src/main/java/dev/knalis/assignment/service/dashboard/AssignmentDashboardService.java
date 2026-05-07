package dev.knalis.assignment.service.dashboard;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.dto.response.AssignmentAdminOverviewResponse;
import dev.knalis.assignment.dto.response.StudentAssignmentDashboardItemResponse;
import dev.knalis.assignment.dto.response.StudentAssignmentDashboardResponse;
import dev.knalis.assignment.dto.response.StudentAssignmentGradeItemResponse;
import dev.knalis.assignment.dto.response.TeacherAssignmentDashboardItemResponse;
import dev.knalis.assignment.dto.response.TeacherAssignmentDashboardResponse;
import dev.knalis.assignment.dto.response.TeacherSubmissionDashboardItemResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Grade;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentDashboardService {

    private static final int DEFAULT_LIST_LIMIT = 5;

    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeRepository gradeRepository;
    private final EducationServiceClient educationServiceClient;

    @Transactional(readOnly = true)
    public StudentAssignmentDashboardResponse getStudentDashboard(UUID userId) {
        List<UUID> groupIds = resolveStudentGroupIds(userId);
        Map<UUID, UUID> topicSubjectIds = resolveStudentTopicSubjectIds(groupIds);
        if (topicSubjectIds.isEmpty()) {
            return new StudentAssignmentDashboardResponse(List.of(), List.of(), List.of());
        }

        List<Assignment> publishedAssignments = groupIds.isEmpty()
                ? List.of()
                : assignmentRepository.findAvailableByTopicIdInForGroups(
                topicSubjectIds.keySet(),
                List.of(AssignmentStatus.PUBLISHED),
                groupIds,
                Instant.now()
        );
        Map<UUID, Instant> availableDeadlines = resolveAvailableDeadlines(publishedAssignments, groupIds);
        List<Submission> submissions = submissionRepository.findAllByUserIdOrderBySubmittedAtDesc(userId);
        Set<UUID> submittedAssignmentIds = submissions.stream()
                .map(Submission::getAssignmentId)
                .collect(Collectors.toSet());

        Map<UUID, Assignment> assignmentsById = publishedAssignments.stream()
                .collect(Collectors.toMap(Assignment::getId, assignment -> assignment, (left, right) -> left, LinkedHashMap::new));
        List<StudentAssignmentDashboardItemResponse> pendingAssignments = publishedAssignments.stream()
                .filter(assignment -> !submittedAssignmentIds.contains(assignment.getId()))
                .sorted(Comparator.comparing(assignment -> resolveDeadline(assignment, availableDeadlines)))
                .map(assignment -> toDashboardItem(
                        assignment,
                        topicSubjectIds.get(assignment.getTopicId()),
                        resolveDeadline(assignment, availableDeadlines),
                        false
                ))
                .toList();

        List<StudentAssignmentGradeItemResponse> recentGrades = buildRecentGrades(
                topicSubjectIds,
                assignmentsById,
                submissions
        );

        return new StudentAssignmentDashboardResponse(
                pendingAssignments.stream().sorted(Comparator.comparing(StudentAssignmentDashboardItemResponse::deadline)).limit(DEFAULT_LIST_LIMIT).toList(),
                pendingAssignments.stream().limit(10).toList(),
                recentGrades
        );
    }

    @Transactional(readOnly = true)
    public AssignmentAdminOverviewResponse getAdminOverview() {
        return new AssignmentAdminOverviewResponse(
                assignmentRepository.countByStatusAndDeadlineAfter(AssignmentStatus.PUBLISHED, Instant.now())
        );
    }

    @Transactional(readOnly = true)
    public TeacherAssignmentDashboardResponse getTeacherDashboard(UUID teacherId) {
        List<Assignment> assignments = assignmentRepository.findAllByCreatedByUserIdAndStatusInOrderByDeadlineAscUpdatedAtDesc(
                teacherId,
                List.of(AssignmentStatus.DRAFT, AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)
        );
        if (assignments.isEmpty()) {
            return TeacherAssignmentDashboardResponse.empty();
        }

        Map<UUID, Assignment> assignmentsById = assignments.stream()
                .collect(Collectors.toMap(Assignment::getId, assignment -> assignment, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, UUID> topicSubjectIds = resolveTopicSubjectIds(assignments);
        List<Submission> submissions = submissionRepository.findTop20ByAssignmentIdInOrderBySubmittedAtDesc(assignmentsById.keySet());
        Map<UUID, Grade> gradesBySubmissionId = gradeRepository.findAllBySubmissionIdIn(
                        submissions.stream().map(Submission::getId).toList()
                ).stream()
                .collect(Collectors.toMap(Grade::getSubmissionId, grade -> grade, (left, right) -> left));

        List<TeacherSubmissionDashboardItemResponse> recentSubmissions = submissions.stream()
                .limit(DEFAULT_LIST_LIMIT)
                .map(this::toTeacherSubmissionItem)
                .toList();
        List<TeacherSubmissionDashboardItemResponse> pendingSubmissionsToReview = submissions.stream()
                .filter(submission -> !gradesBySubmissionId.containsKey(submission.getId()))
                .limit(DEFAULT_LIST_LIMIT)
                .map(this::toTeacherSubmissionItem)
                .toList();
        List<TeacherAssignmentDashboardItemResponse> activeAssignments = assignments.stream()
                .limit(DEFAULT_LIST_LIMIT)
                .map(assignment -> new TeacherAssignmentDashboardItemResponse(
                        assignment.getId(),
                        assignment.getTopicId(),
                        topicSubjectIds.get(assignment.getTopicId()),
                        assignment.getTitle(),
                        assignment.getDeadline(),
                        assignment.getStatus()
                ))
                .toList();

        return new TeacherAssignmentDashboardResponse(
                pendingSubmissionsToReview,
                recentSubmissions,
                activeAssignments
        );
    }

    private List<StudentAssignmentGradeItemResponse> buildRecentGrades(
            Map<UUID, UUID> topicSubjectIds,
            Map<UUID, Assignment> assignmentsById,
            List<Submission> submissions
    ) {
        if (submissions.isEmpty()) {
            return List.of();
        }

        Map<UUID, Submission> submissionsById = submissions.stream()
                .collect(Collectors.toMap(Submission::getId, submission -> submission, (left, right) -> left));
        List<Grade> grades = gradeRepository.findAllBySubmissionIdIn(submissionsById.keySet());

        return grades.stream()
                .sorted(Comparator.comparing(Grade::getCreatedAt).reversed())
                .map(grade -> {
                    Submission submission = submissionsById.get(grade.getSubmissionId());
                    if (submission == null) {
                        return null;
                    }
                    Assignment assignment = assignmentsById.get(submission.getAssignmentId());
                    if (assignment == null) {
                        return null;
                    }
                    return new StudentAssignmentGradeItemResponse(
                            grade.getId(),
                            submission.getId(),
                            assignment.getId(),
                            assignment.getTopicId(),
                            topicSubjectIds.get(assignment.getTopicId()),
                            assignment.getTitle(),
                            grade.getScore(),
                            grade.getFeedback(),
                            grade.getCreatedAt()
                    );
                })
                .filter(Objects::nonNull)
                .limit(DEFAULT_LIST_LIMIT)
                .toList();
    }

    private List<UUID> resolveStudentGroupIds(UUID userId) {
        return educationServiceClient.getGroupsByUser(userId).stream()
                .map(groupMembership -> groupMembership.groupId())
                .distinct()
                .toList();
    }

    private Map<UUID, UUID> resolveStudentTopicSubjectIds(List<UUID> groupIds) {
        Map<UUID, UUID> topicSubjectIds = new LinkedHashMap<>();
        groupIds.forEach(groupId ->
                educationServiceClient.getSubjectsByGroup(groupId).forEach(subject ->
                        educationServiceClient.getTopicsBySubject(subject.id()).forEach(topic ->
                                topicSubjectIds.put(topic.id(), subject.id())
                        )
                )
        );
        return topicSubjectIds;
    }

    private Map<UUID, UUID> resolveTopicSubjectIds(List<Assignment> assignments) {
        Map<UUID, UUID> topicSubjectIds = new LinkedHashMap<>();
        assignments.stream()
                .map(Assignment::getTopicId)
                .distinct()
                .forEach(topicId -> topicSubjectIds.put(topicId, educationServiceClient.getTopic(topicId).subjectId()));
        return topicSubjectIds;
    }

    private StudentAssignmentDashboardItemResponse toDashboardItem(
            Assignment assignment,
            UUID subjectId,
            Instant deadline,
            boolean submitted
    ) {
        return new StudentAssignmentDashboardItemResponse(
                assignment.getId(),
                assignment.getTopicId(),
                subjectId,
                assignment.getTitle(),
                deadline,
                assignment.getStatus(),
                submitted
        );
    }

    private Map<UUID, Instant> resolveAvailableDeadlines(List<Assignment> assignments, List<UUID> groupIds) {
        if (assignments.isEmpty() || groupIds.isEmpty()) {
            return Map.of();
        }
        return assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(
                        assignments.stream().map(Assignment::getId).toList(),
                        groupIds,
                        Instant.now()
                ).stream()
                .collect(Collectors.toMap(
                        availability -> availability.getAssignmentId(),
                        availability -> availability.getDeadline(),
                        (left, right) -> left.isBefore(right) ? left : right
                ));
    }

    private Instant resolveDeadline(Assignment assignment, Map<UUID, Instant> availableDeadlines) {
        return availableDeadlines.getOrDefault(assignment.getId(), assignment.getDeadline());
    }

    private TeacherSubmissionDashboardItemResponse toTeacherSubmissionItem(Submission submission) {
        return new TeacherSubmissionDashboardItemResponse(
                submission.getId(),
                submission.getAssignmentId(),
                submission.getUserId(),
                submission.getSubmittedAt()
        );
    }
}

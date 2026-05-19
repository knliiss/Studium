package dev.knalis.gateway.service;

import dev.knalis.gateway.client.assignment.AssignmentServiceClient;
import dev.knalis.gateway.client.assignment.dto.AssignmentGroupAvailabilityResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentResponse;
import dev.knalis.gateway.client.assignment.dto.SubmissionResponse;
import dev.knalis.gateway.client.auth.AuthServiceClient;
import dev.knalis.gateway.client.auth.dto.UserSummaryResponse;
import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.education.dto.GroupResponse;
import dev.knalis.gateway.client.education.dto.GroupStudentMembershipResponse;
import dev.knalis.gateway.client.education.dto.SubjectResponse;
import dev.knalis.gateway.client.education.dto.TopicResponse;
import dev.knalis.gateway.client.testing.TestingServiceClient;
import dev.knalis.gateway.client.testing.dto.TestGroupAvailabilityResponse;
import dev.knalis.gateway.client.testing.dto.TestResponse;
import dev.knalis.gateway.client.testing.dto.TestResultResponse;
import dev.knalis.gateway.dto.gradebook.AdminGradebookOverviewResponse;
import dev.knalis.gateway.dto.gradebook.AdminGradebookResponse;
import dev.knalis.gateway.dto.gradebook.GradebookActivityRowResponse;
import dev.knalis.gateway.dto.gradebook.GradebookAdminOverviewRowResponse;
import dev.knalis.gateway.dto.gradebook.GradebookFilterOptionResponse;
import dev.knalis.gateway.dto.gradebook.GradebookMatrixActivityResponse;
import dev.knalis.gateway.dto.gradebook.GradebookMatrixCellResponse;
import dev.knalis.gateway.dto.gradebook.GradebookMatrixResponse;
import dev.knalis.gateway.dto.gradebook.GradebookMatrixStudentResponse;
import dev.knalis.gateway.dto.gradebook.GradebookMatrixSummaryResponse;
import dev.knalis.gateway.dto.gradebook.GradebookStudentSubjectResponse;
import dev.knalis.gateway.dto.gradebook.StudentGradebookResponse;
import dev.knalis.gateway.dto.gradebook.TeacherGradebookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GradebookService {

    private static final Logger log = LoggerFactory.getLogger(GradebookService.class);
    private static final int FETCH_SIZE = 200;
    private static final DateTimeFormatter EXPORT_FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private enum ActivityFilter {
        ALL,
        ASSIGNMENTS,
        TESTS
    }

    private record ActivitySnapshot(
            UUID activityId,
            String activityType,
            UUID subjectId,
            UUID topicId,
            String title,
            int maxPoints,
            Instant dueAt,
            boolean allowLate,
            Map<UUID, StudentActivityState> stateByStudent
    ) {
    }

    private record StudentActivityState(
            Integer score,
            Instant submittedAt,
            Instant reviewedAt,
            UUID submissionId,
            UUID resultId,
            boolean late,
            String status
    ) {
    }

    private record SubjectContext(
            SubjectResponse subject,
            GroupResponse primaryGroup,
            List<TopicResponse> topics,
            List<String> teacherNames
    ) {
    }

    private record StudentProgressTotals(
            Integer scoredPoints,
            Integer maxPoints,
            Double averagePercent,
            int gradedCount,
            int pendingCount,
            int missingCount
    ) {
    }

    private final AssignmentServiceClient assignmentServiceClient;
    private final TestingServiceClient testingServiceClient;
    private final EducationServiceClient educationServiceClient;
    private final AuthServiceClient authServiceClient;

    public GradebookService(
            AssignmentServiceClient assignmentServiceClient,
            TestingServiceClient testingServiceClient,
            EducationServiceClient educationServiceClient,
            AuthServiceClient authServiceClient
    ) {
        this.assignmentServiceClient = assignmentServiceClient;
        this.testingServiceClient = testingServiceClient;
        this.educationServiceClient = educationServiceClient;
        this.authServiceClient = authServiceClient;
    }

    public Mono<StudentGradebookResponse> getMyGradebook(UUID userId, String bearerToken, String requestId) {
        return Mono.fromCallable(() -> buildStudentGradebook(userId, bearerToken, requestId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<GradebookStudentSubjectResponse> getMySubjectGradebook(
            UUID userId,
            UUID subjectId,
            String bearerToken,
            String requestId
    ) {
        return Mono.fromCallable(() -> buildStudentSubjectGradebook(userId, subjectId, bearerToken, requestId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TeacherGradebookResponse> getTeacherGradebook(
            UUID teacherId,
            String bearerToken,
            String requestId,
            UUID subjectId,
            UUID groupId,
            String activityType
    ) {
        return Mono.fromCallable(() -> buildTeacherGradebook(teacherId, bearerToken, requestId, subjectId, groupId, activityType))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TeacherGradebookResponse> getTeacherGradebookDetail(
            UUID teacherId,
            UUID subjectId,
            UUID groupId,
            String bearerToken,
            String requestId,
            String activityType
    ) {
        return getTeacherGradebook(teacherId, bearerToken, requestId, subjectId, groupId, activityType);
    }

    public Mono<AdminGradebookOverviewResponse> getAdminOverview(String bearerToken, String requestId, UUID teacherId) {
        return Mono.fromCallable(() -> buildAdminOverview(bearerToken, requestId, teacherId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AdminGradebookResponse> getAdminGradebook(
            String bearerToken,
            String requestId,
            UUID subjectId,
            UUID groupId,
            UUID teacherId,
            String activityType
    ) {
        return Mono.fromCallable(() -> buildAdminGradebook(bearerToken, requestId, subjectId, groupId, teacherId, activityType))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ResponseEntity<byte[]>> exportGradebook(
            UUID userId,
            Set<String> roles,
            String bearerToken,
            String requestId,
            UUID subjectId,
            UUID groupId,
            UUID teacherId,
            String activityType
    ) {
        return Mono.fromCallable(() -> buildExport(userId, roles, bearerToken, requestId, subjectId, groupId, teacherId, activityType))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private StudentGradebookResponse buildStudentGradebook(UUID userId, String bearerToken, String requestId) {
        List<SubjectResponse> subjects = listSubjects(bearerToken, requestId);
        Map<UUID, GroupResponse> groupsById = listGroups(bearerToken, requestId).stream()
                .collect(Collectors.toMap(GroupResponse::id, group -> group, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, String> userNames = resolveUserNames(bearerToken, requestId, collectTeacherIds(subjects));

        List<GradebookStudentSubjectResponse> responses = subjects.stream()
                .map(subject -> buildStudentSubjectGradebook(userId, subject, groupsById, userNames, bearerToken, requestId))
                .sorted(Comparator.comparing(GradebookStudentSubjectResponse::subjectName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalScored = responses.stream().map(GradebookStudentSubjectResponse::scoredPoints).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int totalMax = responses.stream().map(GradebookStudentSubjectResponse::maxPoints).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int pending = responses.stream().mapToInt(GradebookStudentSubjectResponse::pendingCount).sum();
        int missing = responses.stream().mapToInt(GradebookStudentSubjectResponse::missingCount).sum();
        int graded = responses.stream().flatMap(subject -> subject.activities().stream())
                .mapToInt(activity -> "GRADED".equals(activity.status()) ? 1 : 0)
                .sum();

        return new StudentGradebookResponse(percent(totalScored, totalMax), graded, pending, missing, responses);
    }

    private GradebookStudentSubjectResponse buildStudentSubjectGradebook(
            UUID userId,
            UUID subjectId,
            String bearerToken,
            String requestId
    ) {
        SubjectResponse subject = listSubjects(bearerToken, requestId).stream()
                .filter(item -> item.id().equals(subjectId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Subject is not available for current student"));
        Map<UUID, GroupResponse> groupsById = listGroups(bearerToken, requestId).stream()
                .collect(Collectors.toMap(GroupResponse::id, group -> group, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, String> userNames = resolveUserNames(bearerToken, requestId, new LinkedHashSet<>(subject.teacherIds()));
        return buildStudentSubjectGradebook(userId, subject, groupsById, userNames, bearerToken, requestId);
    }

    private GradebookStudentSubjectResponse buildStudentSubjectGradebook(
            UUID userId,
            SubjectResponse subject,
            Map<UUID, GroupResponse> groupsById,
            Map<UUID, String> userNames,
            String bearerToken,
            String requestId
    ) {
        SubjectContext context = subjectContext(subject, groupsById, userNames, bearerToken, requestId);
        List<GradebookActivityRowResponse> activities = new ArrayList<>();
        int assignmentsCompleted = 0;
        int assignmentsTotal = 0;
        int testsCompleted = 0;
        int testsTotal = 0;
        Instant now = Instant.now();

        for (TopicResponse topic : context.topics()) {
            List<AssignmentResponse> assignments = await(assignmentServiceClient.getAssignmentsByTopic(bearerToken, requestId, topic.id(), FETCH_SIZE)).items();
            for (AssignmentResponse assignment : assignments) {
                assignmentsTotal++;
                SubmissionResponse latestSubmission = await(assignmentServiceClient.getMySubmissionsByAssignment(bearerToken, requestId, assignment.id()))
                        .stream()
                        .max(Comparator.comparing(SubmissionResponse::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null);
                GradebookActivityRowResponse activity = toStudentAssignmentActivity(subject.id(), topic, assignment, latestSubmission, now);
                if ("GRADED".equals(activity.status())) {
                    assignmentsCompleted++;
                }
                activities.add(activity);
            }

            List<TestResponse> tests = await(testingServiceClient.getTestsByTopic(bearerToken, requestId, topic.id(), FETCH_SIZE)).items();
            for (TestResponse test : tests) {
                testsTotal++;
                TestResultResponse latestResult = await(testingServiceClient.getMyResultsByTest(bearerToken, requestId, test.id()))
                        .stream()
                        .max(Comparator.comparing(TestResultResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(null);
                GradebookActivityRowResponse activity = toStudentTestActivity(subject.id(), topic, test, latestResult, now);
                if ("GRADED".equals(activity.status())) {
                    testsCompleted++;
                }
                activities.add(activity);
            }
        }

        activities.sort(Comparator.comparing(GradebookActivityRowResponse::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GradebookActivityRowResponse::title, String.CASE_INSENSITIVE_ORDER));
        StudentProgressTotals totals = summarizeStudentActivities(activities);

        return new GradebookStudentSubjectResponse(
                subject.id(),
                subject.name(),
                context.primaryGroup() == null ? null : context.primaryGroup().name(),
                context.teacherNames(),
                totals.averagePercent(),
                totals.scoredPoints(),
                totals.maxPoints(),
                assignmentsCompleted,
                assignmentsTotal,
                testsCompleted,
                testsTotal,
                totals.pendingCount(),
                totals.missingCount(),
                activities
        );
    }

    private TeacherGradebookResponse buildTeacherGradebook(
            UUID teacherId,
            String bearerToken,
            String requestId,
            UUID requestedSubjectId,
            UUID requestedGroupId,
            String activityType
    ) {
        List<SubjectResponse> subjects = listSubjects(bearerToken, requestId);
        if (subjects.isEmpty()) {
            return new TeacherGradebookResponse(List.of(), List.of(), null, null, normalizeActivityFilter(activityType).name(), null);
        }

        Map<UUID, GroupResponse> groupsById = listGroups(bearerToken, requestId).stream()
                .collect(Collectors.toMap(GroupResponse::id, group -> group, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, String> userNames = resolveUserNames(bearerToken, requestId, collectTeacherAndStudentIds(subjects, groupsById, bearerToken, requestId));
        List<GradebookFilterOptionResponse> subjectOptions = subjects.stream()
                .map(subject -> new GradebookFilterOptionResponse(subject.id(), subject.name()))
                .sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER))
                .toList();

        SubjectResponse selectedSubject = selectSubject(subjects, requestedSubjectId);
        UUID selectedGroupId = selectGroupId(selectedSubject, requestedGroupId);
        List<GradebookFilterOptionResponse> groupOptions = selectedSubject.groupIds().stream()
                .map(groupsById::get)
                .filter(Objects::nonNull)
                .map(group -> new GradebookFilterOptionResponse(group.id(), group.name()))
                .sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER))
                .toList();

        GradebookMatrixResponse matrix = selectedSubject == null || selectedGroupId == null
                ? null
                : buildMatrix(selectedSubject, selectedGroupId, bearerToken, requestId, userNames, normalizeActivityFilter(activityType));

        return new TeacherGradebookResponse(
                subjectOptions,
                groupOptions,
                selectedSubject == null ? null : selectedSubject.id(),
                selectedGroupId,
                normalizeActivityFilter(activityType).name(),
                matrix
        );
    }

    private AdminGradebookOverviewResponse buildAdminOverview(String bearerToken, String requestId, UUID teacherId) {
        List<SubjectResponse> subjects = listSubjects(bearerToken, requestId);
        List<GroupResponse> groups = listGroups(bearerToken, requestId);
        Map<UUID, GroupResponse> groupsById = groups.stream()
                .collect(Collectors.toMap(GroupResponse::id, group -> group, (left, right) -> left, LinkedHashMap::new));
        Set<UUID> userIds = collectTeacherAndStudentIds(subjects, groupsById, bearerToken, requestId);
        Map<UUID, String> userNames = resolveUserNames(bearerToken, requestId, userIds);

        List<GradebookAdminOverviewRowResponse> rows = buildAdminRows(subjects, groupsById, bearerToken, requestId, teacherId, userNames);
        double weightedAverageSum = 0d;
        int weightedAverageCount = 0;
        double weightedCompletionSum = 0d;
        int weightedCompletionCount = 0;
        int missingWorkCount = 0;
        for (GradebookAdminOverviewRowResponse row : rows) {
            missingWorkCount += row.missingCount();
            if (row.averagePercent() != null) {
                weightedAverageSum += row.averagePercent() * row.studentCount();
                weightedAverageCount += row.studentCount();
            }
            if (row.completionPercent() != null) {
                weightedCompletionSum += row.completionPercent() * row.studentCount();
                weightedCompletionCount += row.studentCount();
            }
        }

        Set<UUID> activeStudents = new LinkedHashSet<>();
        for (GroupResponse group : groups) {
            for (GroupStudentMembershipResponse membership : await(educationServiceClient.getGroupStudents(bearerToken, requestId, group.id()))) {
                activeStudents.add(membership.userId());
            }
        }

        return new AdminGradebookOverviewResponse(
                weightedAverageCount == 0 ? null : round(weightedAverageSum / weightedAverageCount),
                activeStudents.size(),
                (int) rows.stream().map(GradebookAdminOverviewRowResponse::subjectId).distinct().count(),
                (int) rows.stream().map(GradebookAdminOverviewRowResponse::groupId).distinct().count(),
                missingWorkCount,
                weightedCompletionCount == 0 ? null : round(weightedCompletionSum / weightedCompletionCount),
                subjects.stream().map(subject -> new GradebookFilterOptionResponse(subject.id(), subject.name())).sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER)).toList(),
                groups.stream().map(group -> new GradebookFilterOptionResponse(group.id(), group.name())).sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER)).toList(),
                teacherOptions(subjects, userNames),
                rows
        );
    }

    private AdminGradebookResponse buildAdminGradebook(
            String bearerToken,
            String requestId,
            UUID subjectId,
            UUID groupId,
            UUID teacherId,
            String activityType
    ) {
        List<SubjectResponse> subjects = listSubjects(bearerToken, requestId);
        List<GroupResponse> groups = listGroups(bearerToken, requestId);
        Map<UUID, GroupResponse> groupsById = groups.stream()
                .collect(Collectors.toMap(GroupResponse::id, group -> group, (left, right) -> left, LinkedHashMap::new));
        Set<UUID> userIds = collectTeacherAndStudentIds(subjects, groupsById, bearerToken, requestId);
        Map<UUID, String> userNames = resolveUserNames(bearerToken, requestId, userIds);
        List<GradebookAdminOverviewRowResponse> rows = buildAdminRows(subjects, groupsById, bearerToken, requestId, teacherId, userNames);

        GradebookMatrixResponse matrix = null;
        if (subjectId != null && groupId != null) {
            SubjectResponse selectedSubject = subjects.stream()
                    .filter(subject -> subject.id().equals(subjectId))
                    .findFirst()
                    .orElse(null);
            if (selectedSubject != null && selectedSubject.groupIds().contains(groupId)
                    && (teacherId == null || selectedSubject.teacherIds().contains(teacherId))) {
                matrix = buildMatrix(selectedSubject, groupId, bearerToken, requestId, userNames, normalizeActivityFilter(activityType));
            }
        }

        return new AdminGradebookResponse(
                subjects.stream().map(subject -> new GradebookFilterOptionResponse(subject.id(), subject.name())).sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER)).toList(),
                groups.stream().map(group -> new GradebookFilterOptionResponse(group.id(), group.name())).sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER)).toList(),
                teacherOptions(subjects, userNames),
                subjectId,
                groupId,
                teacherId,
                rows,
                matrix
        );
    }

    private List<GradebookAdminOverviewRowResponse> buildAdminRows(
            List<SubjectResponse> subjects,
            Map<UUID, GroupResponse> groupsById,
            String bearerToken,
            String requestId,
            UUID teacherId,
            Map<UUID, String> userNames
    ) {
        List<GradebookAdminOverviewRowResponse> rows = new ArrayList<>();
        for (SubjectResponse subject : subjects) {
            if (teacherId != null && !subject.teacherIds().contains(teacherId)) {
                continue;
            }
            for (UUID groupId : subject.groupIds()) {
                GroupResponse group = groupsById.get(groupId);
                if (group == null) {
                    continue;
                }
                GradebookMatrixResponse matrix = buildMatrix(subject, groupId, bearerToken, requestId, userNames, ActivityFilter.ALL);
                if (matrix == null) {
                    continue;
                }
                int high = 0;
                int good = 0;
                int average = 0;
                int low = 0;
                int completedCells = 0;
                int totalCells = 0;
                for (GradebookMatrixStudentResponse student : matrix.students()) {
                    if (student.averagePercent() != null) {
                        if (student.averagePercent() >= 90) {
                            high++;
                        } else if (student.averagePercent() >= 75) {
                            good++;
                        } else if (student.averagePercent() >= 60) {
                            average++;
                        } else {
                            low++;
                        }
                    }
                    for (int index = 0; index < student.cells().size(); index++) {
                        GradebookMatrixCellResponse cell = student.cells().get(index);
                        GradebookMatrixActivityResponse activity = matrix.activities().size() > index ? matrix.activities().get(index) : null;
                        if (!countsTowardCompletion(cell, activity, Instant.now())) {
                            continue;
                        }
                        totalCells++;
                        if (isCompletedStatus(cell.status())) {
                            completedCells++;
                        }
                    }
                }
                rows.add(new GradebookAdminOverviewRowResponse(
                        subject.id(),
                        subject.name(),
                        group.id(),
                        group.name(),
                        subject.teacherIds(),
                        subject.teacherIds().stream().map(id -> userNames.getOrDefault(id, shortUserLabel(id))).toList(),
                        matrix.summary().studentCount(),
                        matrix.summary().groupAveragePercent(),
                        high,
                        good,
                        average,
                        low,
                        matrix.summary().missingCount(),
                        totalCells == 0 ? null : round((completedCells * 100d) / totalCells)
                ));
            }
        }
        rows.sort(Comparator.comparing(GradebookAdminOverviewRowResponse::groupName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(GradebookAdminOverviewRowResponse::subjectName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private GradebookMatrixResponse buildMatrix(
            SubjectResponse subject,
            UUID groupId,
            String bearerToken,
            String requestId,
            Map<UUID, String> userNames,
            ActivityFilter activityFilter
    ) {
        GroupResponse group = await(educationServiceClient.getGroup(bearerToken, requestId, groupId));
        List<GroupStudentMembershipResponse> memberships = await(educationServiceClient.getGroupStudents(bearerToken, requestId, groupId));
        List<UUID> studentIds = memberships.stream().map(GroupStudentMembershipResponse::userId).distinct().toList();
        List<TopicResponse> topics = await(educationServiceClient.getTopicsBySubject(bearerToken, requestId, subject.id()));
        List<ActivitySnapshot> activities = new ArrayList<>();
        Instant now = Instant.now();

        if (activityFilter != ActivityFilter.TESTS) {
            for (TopicResponse topic : topics) {
                List<AssignmentResponse> assignments = await(assignmentServiceClient.getAssignmentsByTopic(bearerToken, requestId, topic.id(), FETCH_SIZE)).items();
                for (AssignmentResponse assignment : assignments) {
                    AssignmentGroupAvailabilityResponse availability = await(assignmentServiceClient.getAssignmentAvailability(bearerToken, requestId, assignment.id()))
                            .stream()
                            .filter(item -> item.groupId().equals(groupId) && item.visible())
                            .findFirst()
                            .orElse(null);
                    if (availability == null) {
                        continue;
                    }
                    Map<UUID, List<SubmissionResponse>> submissionsByStudent = await(assignmentServiceClient.getSubmissionsByAssignment(bearerToken, requestId, assignment.id(), FETCH_SIZE))
                            .items()
                            .stream()
                            .collect(Collectors.groupingBy(SubmissionResponse::userId, LinkedHashMap::new, Collectors.toList()));
                    Map<UUID, StudentActivityState> stateByStudent = new LinkedHashMap<>();
                    for (UUID studentId : studentIds) {
                        SubmissionResponse latest = submissionsByStudent.getOrDefault(studentId, List.of()).stream()
                                .max(Comparator.comparing(SubmissionResponse::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                                .orElse(null);
                        stateByStudent.put(studentId, toAssignmentState(availability.deadline(), availability.allowLateSubmissions(), latest, now));
                    }
                    activities.add(new ActivitySnapshot(
                            assignment.id(),
                            "ASSIGNMENT",
                            subject.id(),
                            topic.id(),
                            assignment.title(),
                            assignment.maxPoints(),
                            availability.deadline() == null ? assignment.deadline() : availability.deadline(),
                            availability.allowLateSubmissions(),
                            stateByStudent
                    ));
                }
            }
        }

        if (activityFilter != ActivityFilter.ASSIGNMENTS) {
            for (TopicResponse topic : topics) {
                List<TestResponse> tests = await(testingServiceClient.getTestsByTopic(bearerToken, requestId, topic.id(), FETCH_SIZE)).items();
                for (TestResponse test : tests) {
                    TestGroupAvailabilityResponse availability = await(testingServiceClient.getTestAvailability(bearerToken, requestId, test.id()))
                            .stream()
                            .filter(item -> item.groupId().equals(groupId) && item.visible())
                            .findFirst()
                            .orElse(null);
                    if (availability == null) {
                        continue;
                    }
                    Map<UUID, List<TestResultResponse>> resultsByStudent = await(testingServiceClient.getResultsByTest(bearerToken, requestId, test.id(), FETCH_SIZE))
                            .items()
                            .stream()
                            .collect(Collectors.groupingBy(TestResultResponse::userId, LinkedHashMap::new, Collectors.toList()));
                    Map<UUID, StudentActivityState> stateByStudent = new LinkedHashMap<>();
                    for (UUID studentId : studentIds) {
                        TestResultResponse latest = resultsByStudent.getOrDefault(studentId, List.of()).stream()
                                .max(Comparator.comparing(TestResultResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                                .orElse(null);
                        stateByStudent.put(studentId, toTestState(availability.deadline() == null ? availability.availableUntil() : availability.deadline(), latest, now));
                    }
                    activities.add(new ActivitySnapshot(
                            test.id(),
                            "TEST",
                            subject.id(),
                            topic.id(),
                            test.title(),
                            test.maxPoints(),
                            availability.deadline() == null ? availability.availableUntil() : availability.deadline(),
                            false,
                            stateByStudent
                    ));
                }
            }
        }

        activities.sort(Comparator.comparing(ActivitySnapshot::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ActivitySnapshot::title, String.CASE_INSENSITIVE_ORDER));

        List<GradebookMatrixActivityResponse> activityResponses = activities.stream()
                .map(activity -> new GradebookMatrixActivityResponse(activity.activityId(), activity.activityType(), activity.title(), activity.maxPoints(), activity.dueAt()))
                .toList();
        List<GradebookMatrixStudentResponse> studentResponses = new ArrayList<>();
        List<String> teacherNames = subject.teacherIds().stream().map(id -> userNames.getOrDefault(id, shortUserLabel(id))).toList();
        double averageSum = 0d;
        int averageCount = 0;
        Double highest = null;
        Double lowest = null;
        int totalMissing = 0;
        int totalPending = 0;

        for (UUID studentId : studentIds) {
            List<GradebookMatrixCellResponse> cells = new ArrayList<>();
            int scoredPoints = 0;
            int maxPoints = 0;
            int missingCount = 0;
            for (ActivitySnapshot activity : activities) {
                StudentActivityState state = activity.stateByStudent().get(studentId);
                if (state != null && state.score() != null && "GRADED".equals(state.status())) {
                    scoredPoints += state.score();
                    maxPoints += activity.maxPoints();
                }
                if (state != null) {
                    if ("MISSING".equals(state.status())) {
                        missingCount++;
                        totalMissing++;
                    }
                    if ("PENDING_REVIEW".equals(state.status())) {
                        totalPending++;
                    }
                }
                cells.add(new GradebookMatrixCellResponse(
                        activity.activityId(),
                        state == null ? null : state.score(),
                        activity.maxPoints(),
                        state == null ? null : state.score() == null ? null : percent(state.score(), activity.maxPoints()),
                        state == null ? "NOT_SUBMITTED" : state.status(),
                        state != null && state.late(),
                        state == null ? null : state.submittedAt(),
                        state == null ? null : state.reviewedAt(),
                        state == null ? null : state.submissionId(),
                        state == null ? null : state.resultId()
                ));
            }
            Double averagePercent = percent(scoredPoints, maxPoints);
            if (averagePercent != null) {
                averageSum += averagePercent;
                averageCount++;
                highest = highest == null ? averagePercent : Math.max(highest, averagePercent);
                lowest = lowest == null ? averagePercent : Math.min(lowest, averagePercent);
            }
            studentResponses.add(new GradebookMatrixStudentResponse(
                    studentId,
                    userNames.getOrDefault(studentId, shortUserLabel(studentId)),
                    averagePercent,
                    missingCount,
                    cells
            ));
        }
        studentResponses.sort(Comparator.comparing(GradebookMatrixStudentResponse::studentName, String.CASE_INSENSITIVE_ORDER));

        return new GradebookMatrixResponse(
                subject.id(),
                subject.name(),
                groupId,
                group == null ? shortUserLabel(groupId) : group.name(),
                teacherNames,
                activityFilter.name(),
                new GradebookMatrixSummaryResponse(
                        studentResponses.size(),
                        averageCount == 0 ? null : round(averageSum / averageCount),
                        highest == null ? null : round(highest),
                        lowest == null ? null : round(lowest),
                        totalMissing,
                        totalPending
                ),
                activityResponses,
                studentResponses
        );
    }

    private ResponseEntity<byte[]> buildExport(
            UUID userId,
            Set<String> roles,
            String bearerToken,
            String requestId,
            UUID subjectId,
            UUID groupId,
            UUID teacherId,
            String activityType
    ) {
        byte[] content;
        String filename;
        String exportMode;
        List<GradebookAdminOverviewRowResponse> filteredOverviewRows;
        log.info(
                "Gradebook export requested: requestId={}, roles={}, hasSubjectId={}, hasGroupId={}, hasTeacherId={}, activityType={}",
                requestId,
                roles,
                subjectId != null,
                groupId != null,
                teacherId != null,
                normalizeActivityFilter(activityType).name()
        );
        if (roles.contains("ROLE_OWNER") || roles.contains("ROLE_ADMIN")) {
            boolean exportDetailMatrix = subjectId != null && groupId != null;
            if (exportDetailMatrix) {
                GradebookMatrixResponse matrix = buildAdminGradebook(bearerToken, requestId, subjectId, groupId, teacherId, activityType).matrix();
                content = matrixCsv(matrix).getBytes(StandardCharsets.UTF_8);
                filename = exportFilename("gradebook-admin-detail");
                exportMode = "admin-detail";
            } else {
                filteredOverviewRows = filterOverviewRows(buildAdminOverview(bearerToken, requestId, teacherId).rows(), subjectId, groupId);
                content = overviewCsv(filteredOverviewRows).getBytes(StandardCharsets.UTF_8);
                filename = exportFilename("gradebook-admin-overview");
                exportMode = "admin-overview";
            }
        } else if (roles.contains("ROLE_TEACHER")) {
            TeacherGradebookResponse response = buildTeacherGradebook(userId, bearerToken, requestId, subjectId, groupId, activityType);
            content = matrixCsv(response.matrix()).getBytes(StandardCharsets.UTF_8);
            filename = exportFilename("gradebook-teacher");
            exportMode = "teacher";
        } else {
            content = studentCsv(buildStudentGradebook(userId, bearerToken, requestId)).getBytes(StandardCharsets.UTF_8);
            filename = exportFilename("gradebook-student");
            exportMode = "student";
        }
        log.info(
                "Gradebook export resolved: requestId={}, mode={}, filename={}, bytes={}",
                requestId,
                exportMode,
                filename,
                content.length
        );
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content);
    }

    private String studentCsv(StudentGradebookResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("subject,teacher,activityType,title,status,score,maxPoints,percentage,dueAt\n");
        for (GradebookStudentSubjectResponse subject : response.subjects()) {
            String teacherLabel = String.join(" | ", subject.teacherNames());
            for (GradebookActivityRowResponse activity : subject.activities()) {
                builder.append(csv(subject.subjectName())).append(',')
                        .append(csv(teacherLabel)).append(',')
                        .append(csv(activity.activityType())).append(',')
                        .append(csv(activity.title())).append(',')
                        .append(csv(activity.status())).append(',')
                        .append(csv(activity.score())).append(',')
                        .append(csv(activity.maxPoints())).append(',')
                        .append(csv(activity.percentage())).append(',')
                        .append(csv(activity.dueAt())).append('\n');
            }
        }
        return builder.toString();
    }

    private String overviewCsv(List<GradebookAdminOverviewRowResponse> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("subject,group,teachers,students,averagePercent,high,good,average,low,missing,completionPercent\n");
        for (GradebookAdminOverviewRowResponse row : rows) {
            builder.append(csv(row.subjectName())).append(',')
                    .append(csv(row.groupName())).append(',')
                    .append(csv(String.join(" | ", row.teacherNames()))).append(',')
                    .append(csv(row.studentCount())).append(',')
                    .append(csv(row.averagePercent())).append(',')
                    .append(csv(row.highCount())).append(',')
                    .append(csv(row.goodCount())).append(',')
                    .append(csv(row.averageCount())).append(',')
                    .append(csv(row.lowCount())).append(',')
                    .append(csv(row.missingCount())).append(',')
                    .append(csv(row.completionPercent())).append('\n');
        }
        return builder.toString();
    }

    private String matrixCsv(GradebookMatrixResponse matrix) {
        if (matrix == null) {
            return "student,averagePercent,missingCount\n";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("student");
        for (GradebookMatrixActivityResponse activity : matrix.activities()) {
            builder.append(',').append(csv(activity.title() + " (" + activity.maxPoints() + ")"));
        }
        builder.append(',').append("averagePercent")
                .append(',').append("missingCount")
                .append('\n');
        for (GradebookMatrixStudentResponse student : matrix.students()) {
            builder.append(csv(student.studentName()));
            for (GradebookMatrixCellResponse cell : student.cells()) {
                String value = cell.score() == null ? cell.status() : cell.score() + "/" + cell.maxPoints();
                builder.append(',').append(csv(value));
            }
            builder.append(',').append(csv(student.averagePercent()))
                    .append(',').append(csv(student.missingCount()))
                    .append('\n');
        }
        return builder.toString();
    }

    private StudentProgressTotals summarizeStudentActivities(List<GradebookActivityRowResponse> activities) {
        int scored = 0;
        int max = 0;
        int graded = 0;
        int pending = 0;
        int missing = 0;
        for (GradebookActivityRowResponse activity : activities) {
            if ("GRADED".equals(activity.status()) && activity.score() != null) {
                scored += activity.score();
                max += activity.maxPoints();
                graded++;
            } else if ("PENDING_REVIEW".equals(activity.status()) || "RETURNED_FOR_REVISION".equals(activity.status())) {
                pending++;
            } else if ("MISSING".equals(activity.status())) {
                missing++;
            }
        }
        return new StudentProgressTotals(max == 0 ? null : scored, max == 0 ? null : max, percent(scored, max), graded, pending, missing);
    }

    private GradebookActivityRowResponse toStudentAssignmentActivity(
            UUID subjectId,
            TopicResponse topic,
            AssignmentResponse assignment,
            SubmissionResponse submission,
            Instant now
    ) {
        StudentActivityState state = toAssignmentState(assignment.deadline(), assignment.allowLateSubmissions(), submission, now);
        return new GradebookActivityRowResponse(
                assignment.id(),
                "ASSIGNMENT",
                subjectId,
                topic.id(),
                assignment.title(),
                state.score(),
                assignment.maxPoints(),
                state.score() == null ? null : percent(state.score(), assignment.maxPoints()),
                state.status(),
                state.submittedAt(),
                state.reviewedAt(),
                assignment.deadline(),
                state.late(),
                state.submissionId(),
                null
        );
    }

    private GradebookActivityRowResponse toStudentTestActivity(
            UUID subjectId,
            TopicResponse topic,
            TestResponse test,
            TestResultResponse result,
            Instant now
    ) {
        StudentActivityState state = toTestState(test.availableUntil(), result, now);
        return new GradebookActivityRowResponse(
                test.id(),
                "TEST",
                subjectId,
                topic.id(),
                test.title(),
                state.score(),
                test.maxPoints(),
                state.score() == null ? null : percent(state.score(), test.maxPoints()),
                state.status(),
                state.submittedAt(),
                state.reviewedAt(),
                test.availableUntil(),
                false,
                null,
                state.resultId()
        );
    }

    private StudentActivityState toAssignmentState(
            Instant dueAt,
            boolean allowLateSubmissions,
            SubmissionResponse submission,
            Instant now
    ) {
        if (submission != null) {
            Instant effectiveDueAt = "RETURNED_FOR_REVISION".equals(submission.status()) && submission.revisionDeadline() != null
                    ? submission.revisionDeadline()
                    : dueAt;
            boolean late = submission.submittedLate()
                    || (effectiveDueAt != null && submission.submittedAt() != null && submission.submittedAt().isAfter(effectiveDueAt));
            if (submission.score() != null || "GRADED".equals(submission.status())) {
                return new StudentActivityState(submission.score(), submission.submittedAt(), submission.gradedAt(), submission.id(), null, late, "GRADED");
            }
            if ("RETURNED_FOR_REVISION".equals(submission.status())) {
                if (effectiveDueAt != null && now.isAfter(effectiveDueAt)) {
                    return new StudentActivityState(null, submission.submittedAt(), submission.reviewedAt(), submission.id(), null, true, "MISSING");
                }
                return new StudentActivityState(null, submission.submittedAt(), submission.reviewedAt(), submission.id(), null, late, "RETURNED_FOR_REVISION");
            }
            return new StudentActivityState(null, submission.submittedAt(), submission.gradedAt(), submission.id(), null, late, "PENDING_REVIEW");
        }
        if (dueAt != null && now.isAfter(dueAt) && !allowLateSubmissions) {
            return new StudentActivityState(null, null, null, null, null, false, "MISSING");
        }
        return new StudentActivityState(null, null, null, null, null, false, "NOT_SUBMITTED");
    }

    private StudentActivityState toTestState(Instant dueAt, TestResultResponse result, Instant now) {
        if (result != null) {
            if (result.reviewedAt() != null) {
                return new StudentActivityState(result.score(), result.createdAt(), result.reviewedAt(), null, result.id(), false, "GRADED");
            }
            return new StudentActivityState(result.score(), result.createdAt(), null, null, result.id(), false, "PENDING_REVIEW");
        }
        if (dueAt != null && now.isAfter(dueAt)) {
            return new StudentActivityState(null, null, null, null, null, false, "MISSING");
        }
        return new StudentActivityState(null, null, null, null, null, false, "NOT_SUBMITTED");
    }

    private SubjectContext subjectContext(
            SubjectResponse subject,
            Map<UUID, GroupResponse> groupsById,
            Map<UUID, String> userNames,
            String bearerToken,
            String requestId
    ) {
        GroupResponse primaryGroup = subject.groupIds().stream().findFirst().map(groupsById::get).orElse(null);
        List<TopicResponse> topics = await(educationServiceClient.getTopicsBySubject(bearerToken, requestId, subject.id()));
        List<String> teacherNames = subject.teacherIds().stream()
                .map(teacherId -> userNames.getOrDefault(teacherId, shortUserLabel(teacherId)))
                .toList();
        return new SubjectContext(subject, primaryGroup, topics, teacherNames);
    }

    private SubjectResponse selectSubject(List<SubjectResponse> subjects, UUID requestedSubjectId) {
        if (subjects.isEmpty()) {
            return null;
        }
        if (requestedSubjectId == null) {
            return subjects.get(0);
        }
        return subjects.stream()
                .filter(subject -> subject.id().equals(requestedSubjectId))
                .findFirst()
                .orElse(subjects.get(0));
    }

    private UUID selectGroupId(SubjectResponse subject, UUID requestedGroupId) {
        if (subject == null || subject.groupIds().isEmpty()) {
            return null;
        }
        if (requestedGroupId != null && subject.groupIds().contains(requestedGroupId)) {
            return requestedGroupId;
        }
        return subject.groupIds().get(0);
    }

    private List<SubjectResponse> listSubjects(String bearerToken, String requestId) {
        return await(educationServiceClient.listSubjects(bearerToken, requestId, FETCH_SIZE)).items();
    }

    private List<GroupResponse> listGroups(String bearerToken, String requestId) {
        return await(educationServiceClient.listGroups(bearerToken, requestId, FETCH_SIZE)).items();
    }

    private Set<UUID> collectTeacherIds(List<SubjectResponse> subjects) {
        return subjects.stream()
                .flatMap(subject -> subject.teacherIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<UUID> collectTeacherAndStudentIds(
            List<SubjectResponse> subjects,
            Map<UUID, GroupResponse> groupsById,
            String bearerToken,
            String requestId
    ) {
        Set<UUID> userIds = collectTeacherIds(subjects);
        for (UUID groupId : subjects.stream().flatMap(subject -> subject.groupIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new))) {
            if (!groupsById.containsKey(groupId)) {
                continue;
            }
            for (GroupStudentMembershipResponse student : await(educationServiceClient.getGroupStudents(bearerToken, requestId, groupId))) {
                userIds.add(student.userId());
            }
        }
        return userIds;
    }

    private Map<UUID, String> resolveUserNames(String bearerToken, String requestId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return await(authServiceClient.lookupUsers(bearerToken, requestId, new ArrayList<>(new LinkedHashSet<>(userIds)))).stream()
                .collect(Collectors.toMap(UserSummaryResponse::id, UserSummaryResponse::username, (left, right) -> left, LinkedHashMap::new));
    }

    private List<GradebookFilterOptionResponse> teacherOptions(List<SubjectResponse> subjects, Map<UUID, String> userNames) {
        return subjects.stream()
                .flatMap(subject -> subject.teacherIds().stream())
                .distinct()
                .map(teacherId -> new GradebookFilterOptionResponse(teacherId, userNames.getOrDefault(teacherId, shortUserLabel(teacherId))))
                .sorted(Comparator.comparing(GradebookFilterOptionResponse::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ActivityFilter normalizeActivityFilter(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ActivityFilter.ALL;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ASSIGN")) {
            return ActivityFilter.ASSIGNMENTS;
        }
        if (normalized.startsWith("TEST")) {
            return ActivityFilter.TESTS;
        }
        return ActivityFilter.ALL;
    }

    private Double percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return round((numerator * 100d) / denominator);
    }

    private Double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private boolean countsTowardCompletion(
            GradebookMatrixCellResponse cell,
            GradebookMatrixActivityResponse activity,
            Instant now
    ) {
        if (cell == null) {
            return false;
        }
        if (isCompletedStatus(cell.status()) || "MISSING".equals(cell.status())) {
            return true;
        }
        return activity != null && activity.dueAt() != null && !activity.dueAt().isAfter(now);
    }

    private boolean isCompletedStatus(String status) {
        return "GRADED".equals(status) || "PENDING_REVIEW".equals(status);
    }

    private String exportFilename(String prefix) {
        return prefix + "-" + EXPORT_FILENAME_TIMESTAMP.format(Instant.now()) + ".csv";
    }

    private List<GradebookAdminOverviewRowResponse> filterOverviewRows(
            List<GradebookAdminOverviewRowResponse> rows,
            UUID subjectId,
            UUID groupId
    ) {
        return rows.stream()
                .filter(row -> subjectId == null || row.subjectId().equals(subjectId))
                .filter(row -> groupId == null || row.groupId().equals(groupId))
                .toList();
    }

    private String shortUserLabel(UUID id) {
        return id.toString().substring(0, 8);
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return '"' + text + '"';
    }

    private <T> T await(Mono<T> mono) {
        return Objects.requireNonNull(mono.block());
    }
}

package dev.knalis.gateway.service;

import dev.knalis.gateway.client.analytics.AnalyticsServiceClient;
import dev.knalis.gateway.client.analytics.dto.DashboardOverviewResponse;
import dev.knalis.gateway.client.analytics.dto.GroupOverviewResponse;
import dev.knalis.gateway.client.analytics.dto.StudentAnalyticsResponse;
import dev.knalis.gateway.client.analytics.dto.StudentRiskResponse;
import dev.knalis.gateway.client.assignment.AssignmentServiceClient;
import dev.knalis.gateway.client.assignment.dto.AssignmentAdminOverviewResponse;
import dev.knalis.gateway.client.assignment.dto.StudentAssignmentDashboardResponse;
import dev.knalis.gateway.client.assignment.dto.TeacherAssignmentDashboardResponse;
import dev.knalis.gateway.client.audit.AuditServiceClient;
import dev.knalis.gateway.client.audit.dto.AuditEventPageResponse;
import dev.knalis.gateway.client.auth.AuthServiceClient;
import dev.knalis.gateway.client.auth.dto.AdminUserStatsResponse;
import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.education.dto.EducationAdminOverviewResponse;
import dev.knalis.gateway.client.notification.NotificationServiceClient;
import dev.knalis.gateway.client.notification.dto.NotificationPageResponse;
import dev.knalis.gateway.client.notification.dto.UnreadCountResponse;
import dev.knalis.gateway.client.schedule.ScheduleServiceClient;
import dev.knalis.gateway.client.schedule.dto.LessonSlotResponse;
import dev.knalis.gateway.client.testing.TestingServiceClient;
import dev.knalis.gateway.client.testing.dto.StudentTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TeacherTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TestingAdminOverviewResponse;
import dev.knalis.gateway.dto.AdminDashboardAnalyticsSummaryResponse;
import dev.knalis.gateway.dto.AdminDashboardOverviewResponse;
import dev.knalis.gateway.dto.DashboardAssignmentItemResponse;
import dev.knalis.gateway.dto.DashboardAuditEventResponse;
import dev.knalis.gateway.dto.DashboardDeadlineItemResponse;
import dev.knalis.gateway.dto.DashboardGradeItemResponse;
import dev.knalis.gateway.dto.DashboardGroupRiskResponse;
import dev.knalis.gateway.dto.DashboardProgressSummaryResponse;
import dev.knalis.gateway.dto.DashboardScheduleChangeResponse;
import dev.knalis.gateway.dto.DashboardSubmissionItemResponse;
import dev.knalis.gateway.dto.DashboardTestItemResponse;
import dev.knalis.gateway.dto.ResolvedLessonResponse;
import dev.knalis.gateway.dto.StudentDashboardResponse;
import dev.knalis.gateway.dto.TeacherDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<String> SCHEDULE_CHANGE_TYPES = List.of(
            "SCHEDULE_LESSON_CANCELLED",
            "SCHEDULE_LESSON_CHANGED",
            "SCHEDULE_EXTRA_LESSON",
            "SCHEDULE_ROOM_CHANGED",
            "SCHEDULE_FORMAT_CHANGED",
            "SCHEDULE_TEACHER_CHANGED"
    );

    private final MyScheduleService myScheduleService;
    private final AssignmentServiceClient assignmentServiceClient;
    private final TestingServiceClient testingServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final ScheduleServiceClient scheduleServiceClient;
    private final AuthServiceClient authServiceClient;
    private final EducationServiceClient educationServiceClient;
    private final AuditServiceClient auditServiceClient;

    public Mono<StudentDashboardResponse> getStudentDashboard(UUID userId, String bearerToken, String requestId) {
        LocalDate today = LocalDate.now();
        return Mono.zip(
                        safe(
                                myScheduleService.getMyRange(
                                        userId,
                                        bearerToken,
                                        requestId,
                                        today,
                                        today,
                                        Set.of("ROLE_STUDENT")
                                ),
                                List.of(),
                                "student dashboard schedule"
                        ),
                        safe(
                                assignmentServiceClient.getStudentDashboard(bearerToken, requestId),
                                StudentAssignmentDashboardResponse.empty(),
                                "student dashboard assignments"
                        ),
                        safe(
                                testingServiceClient.getStudentDashboard(bearerToken, requestId),
                                StudentTestDashboardResponse.empty(),
                                "student dashboard tests"
                        ),
                        safe(
                                notificationServiceClient.getUnreadCount(bearerToken, requestId),
                                new UnreadCountResponse(0),
                                "student dashboard notifications"
                        ),
                        safe(
                                analyticsServiceClient.getStudentAnalytics(bearerToken, requestId, userId),
                                new StudentAnalyticsResponse(
                                        userId, null, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, null, null, null
                                ),
                                "student dashboard analytics"
                        ),
                        safe(
                                analyticsServiceClient.getStudentRisk(bearerToken, requestId, userId),
                                new StudentRiskResponse(
                                        userId, null, null, null, 0, 0, 0, null, null
                                ),
                                "student dashboard risk"
                        )
                )
                .map(tuple -> new StudentDashboardResponse(
                        tuple.getT1(),
                        mergeDeadlines(tuple.getT2(), tuple.getT3()),
                        tuple.getT2().recentGrades().stream().map(item -> new DashboardGradeItemResponse(
                                item.gradeId(),
                                item.submissionId(),
                                item.assignmentId(),
                                item.topicId(),
                                item.subjectId(),
                                item.assignmentTitle(),
                                item.score(),
                                item.feedback(),
                                item.gradedAt()
                        )).toList(),
                        tuple.getT4().unreadCount(),
                        new DashboardProgressSummaryResponse(
                                tuple.getT5().averageScore(),
                                tuple.getT5().activityScore(),
                                tuple.getT5().disciplineScore(),
                                tuple.getT5().assignmentsSubmittedCount(),
                                tuple.getT5().testsCompletedCount(),
                                tuple.getT5().missedDeadlinesCount()
                        ),
                        tuple.getT6().riskLevel(),
                        tuple.getT2().pendingAssignments().stream().map(item -> new DashboardAssignmentItemResponse(
                                item.assignmentId(),
                                item.topicId(),
                                item.subjectId(),
                                item.title(),
                                item.deadline(),
                                item.status(),
                                item.submitted()
                        )).toList(),
                        tuple.getT3().availableTests().stream().map(item -> new DashboardTestItemResponse(
                                item.testId(),
                                item.topicId(),
                                item.subjectId(),
                                item.title(),
                                item.status(),
                                item.availableFrom(),
                                item.availableUntil(),
                                item.timeLimitMinutes(),
                                item.attemptsUsed(),
                                item.maxAttempts()
                        )).toList()
                ));
    }

    public Mono<TeacherDashboardResponse> getTeacherDashboard(UUID teacherId, String bearerToken, String requestId) {
        LocalDate today = LocalDate.now();
        return Mono.zip(
                        safe(
                                teacherLessons(teacherId, bearerToken, requestId, today),
                                List.of(),
                                "teacher dashboard schedule"
                        ),
                        safe(
                                assignmentServiceClient.getTeacherDashboard(bearerToken, requestId),
                                TeacherAssignmentDashboardResponse.empty(),
                                "teacher dashboard assignments"
                        ),
                        safe(
                                testingServiceClient.getTeacherDashboard(bearerToken, requestId),
                                TeacherTestDashboardResponse.empty(),
                                "teacher dashboard tests"
                        ),
                        safe(
                                notificationServiceClient.getNotifications(bearerToken, requestId, 0, 20),
                                NotificationPageResponse.empty(),
                                "teacher dashboard notifications"
                        ),
                        safe(
                                analyticsServiceClient.getTeacherGroupsAtRisk(bearerToken, requestId, teacherId),
                                List.of(),
                                "teacher dashboard group risks"
                        )
                )
                .map(tuple -> new TeacherDashboardResponse(
                        tuple.getT1(),
                        tuple.getT2().pendingSubmissionsToReview().stream()
                                .map(item -> new DashboardSubmissionItemResponse(
                                        item.submissionId(),
                                        item.assignmentId(),
                                        item.studentId(),
                                        item.submittedAt()
                                ))
                                .toList(),
                        tuple.getT2().recentSubmissions().stream()
                                .map(item -> new DashboardSubmissionItemResponse(
                                        item.submissionId(),
                                        item.assignmentId(),
                                        item.studentId(),
                                        item.submittedAt()
                                ))
                                .toList(),
                        tuple.getT5().stream()
                                .map(this::toDashboardGroupRisk)
                                .toList(),
                        tuple.getT2().activeAssignments().stream()
                                .map(item -> new DashboardAssignmentItemResponse(
                                        item.assignmentId(),
                                        item.topicId(),
                                        item.subjectId(),
                                        item.title(),
                                        item.deadline(),
                                        item.status(),
                                        false
                                ))
                                .toList(),
                        tuple.getT3().activeTests().stream()
                                .map(item -> new DashboardTestItemResponse(
                                        item.testId(),
                                        item.topicId(),
                                        item.subjectId(),
                                        item.title(),
                                        item.status(),
                                        item.availableFrom(),
                                        item.availableUntil(),
                                        item.timeLimitMinutes(),
                                        item.attemptsUsed(),
                                        item.maxAttempts()
                                ))
                                .toList(),
                        tuple.getT4().items().stream()
                                .filter(item -> SCHEDULE_CHANGE_TYPES.contains(item.type()))
                                .map(item -> new DashboardScheduleChangeResponse(
                                        item.id(),
                                        item.type(),
                                        item.title(),
                                        item.body(),
                                        item.createdAt()
                                ))
                                .toList()
                ));
    }

    public Mono<AdminDashboardOverviewResponse> getAdminDashboard(String bearerToken, String requestId) {
        return Mono.zip(
                        safe(
                                authServiceClient.getAdminStats(bearerToken, requestId),
                                new AdminUserStatsResponse(0, 0, 0, 0, 0, 0, 0, 0),
                                "admin dashboard auth stats"
                        ),
                        safe(
                                educationServiceClient.getAdminOverview(bearerToken, requestId),
                                new EducationAdminOverviewResponse(0, 0, 0),
                                "admin dashboard education"
                        ),
                        safe(
                                analyticsServiceClient.getDashboardOverview(bearerToken, requestId),
                                new DashboardOverviewResponse(0, 0, 0, 0, 0, 0, 0, 0, 0),
                                "admin dashboard analytics"
                        ),
                        safe(
                                assignmentServiceClient.getAdminOverview(bearerToken, requestId),
                                new AssignmentAdminOverviewResponse(0),
                                "admin dashboard assignments"
                        ),
                        safe(
                                testingServiceClient.getAdminOverview(bearerToken, requestId),
                                new TestingAdminOverviewResponse(0),
                                "admin dashboard tests"
                        ),
                        safe(
                                auditServiceClient.getAuditEvents(bearerToken, requestId, 0, 10, "occurredAt", "desc"),
                                AuditEventPageResponse.empty(),
                                "admin dashboard audit"
                        )
                )
                .map(tuple -> new AdminDashboardOverviewResponse(
                        tuple.getT1().totalStudents(),
                        tuple.getT1().totalTeachers(),
                        tuple.getT2().totalGroups(),
                        tuple.getT2().totalSubjects(),
                        tuple.getT3().highRiskStudentsCount(),
                        tuple.getT4().activeDeadlinesCount() + tuple.getT5().activeDeadlinesCount(),
                        tuple.getT6().items().stream().map(item -> new DashboardAuditEventResponse(
                                item.id(),
                                item.actorUserId(),
                                item.action(),
                                item.entityType(),
                                item.entityId(),
                                item.occurredAt(),
                                item.sourceService()
                        )).toList(),
                        new AdminDashboardAnalyticsSummaryResponse(
                                tuple.getT3().totalStudentsTracked(),
                                tuple.getT3().lowRiskStudentsCount(),
                                tuple.getT3().mediumRiskStudentsCount(),
                                tuple.getT3().highRiskStudentsCount(),
                                tuple.getT3().averagePlatformScore(),
                                tuple.getT3().averageDisciplineScore(),
                                tuple.getT3().averageActivityScore(),
                                tuple.getT3().totalMissedDeadlines(),
                                tuple.getT3().totalLateSubmissions()
                        )
                ));
    }

    private List<DashboardDeadlineItemResponse> mergeDeadlines(
            StudentAssignmentDashboardResponse assignmentDashboard,
            StudentTestDashboardResponse testingDashboard
    ) {
        return Stream.concat(
                        assignmentDashboard.upcomingDeadlines().stream().map(item -> new DashboardDeadlineItemResponse(
                                "ASSIGNMENT",
                                item.assignmentId(),
                                item.topicId(),
                                item.subjectId(),
                                item.title(),
                                item.deadline()
                        )),
                        testingDashboard.upcomingDeadlines().stream().map(item -> new DashboardDeadlineItemResponse(
                                "TEST",
                                item.testId(),
                                item.topicId(),
                                item.subjectId(),
                                item.title(),
                                item.availableUntil()
                        ))
                )
                .sorted(Comparator.comparing(DashboardDeadlineItemResponse::deadline, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(10)
                .toList();
    }

    private <T> Mono<T> safe(Mono<T> mono, T fallback, String label) {
        return mono.onErrorResume(exception -> {
            log.warn("Falling back to default for {}", label, exception);
            return Mono.just(fallback);
        });
    }

    private Mono<List<ResolvedLessonResponse>> teacherLessons(
            UUID teacherId,
            String bearerToken,
            String requestId,
            LocalDate date
    ) {
        return Mono.zip(
                        scheduleServiceClient.getTeacherRange(bearerToken, requestId, teacherId, date, date),
                        scheduleServiceClient.getLessonSlots(bearerToken, requestId)
                )
                .map(tuple -> sortLessons(tuple.getT1(), tuple.getT2()));
    }

    private List<ResolvedLessonResponse> sortLessons(
            List<ResolvedLessonResponse> lessons,
            List<LessonSlotResponse> lessonSlots
    ) {
        Map<UUID, Integer> slotNumbersById = lessonSlots.stream()
                .collect(Collectors.toMap(LessonSlotResponse::id, LessonSlotResponse::number));
        return lessons.stream()
                .sorted(Comparator
                        .comparing(ResolvedLessonResponse::date)
                        .thenComparing(response -> slotNumbersById.getOrDefault(response.slotId(), Integer.MAX_VALUE))
                        .thenComparing(response -> response.groupId().toString())
                        .thenComparing(response -> response.subjectId().toString())
                        .thenComparing(response -> response.slotId().toString()))
                .toList();
    }

    private DashboardGroupRiskResponse toDashboardGroupRisk(GroupOverviewResponse groupOverview) {
        String riskLevel = groupOverview.highRiskStudentsCount() > 0 ? "HIGH" : "MEDIUM";
        long affectedStudentsCount = groupOverview.highRiskStudentsCount() > 0
                ? groupOverview.highRiskStudentsCount()
                : groupOverview.mediumRiskStudentsCount();
        return new DashboardGroupRiskResponse(groupOverview.groupId(), riskLevel, affectedStudentsCount);
    }
}

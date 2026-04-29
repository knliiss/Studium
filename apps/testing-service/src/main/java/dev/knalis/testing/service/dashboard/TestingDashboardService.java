package dev.knalis.testing.service.dashboard;

import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.dto.response.StudentTestDashboardItemResponse;
import dev.knalis.testing.dto.response.StudentTestDashboardResponse;
import dev.knalis.testing.dto.response.TeacherTestDashboardResponse;
import dev.knalis.testing.dto.response.TestingAdminOverviewResponse;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestGroupAvailability;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestGroupAvailabilityRepository;
import dev.knalis.testing.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestingDashboardService {

    private static final int DEFAULT_LIST_LIMIT = 5;

    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final TestGroupAvailabilityRepository testGroupAvailabilityRepository;
    private final EducationServiceClient educationServiceClient;

    @Transactional(readOnly = true)
    public StudentTestDashboardResponse getStudentDashboard(UUID userId) {
        List<UUID> groupIds = resolveStudentGroupIds(userId);
        Map<UUID, UUID> topicSubjectIds = resolveStudentTopicSubjectIds(groupIds);
        if (topicSubjectIds.isEmpty()) {
            return new StudentTestDashboardResponse(List.of(), List.of());
        }

        Instant now = Instant.now();
        Map<UUID, Long> attemptsByTestId = testAttemptRepository.findAllByUserId(userId).stream()
                .collect(Collectors.groupingBy(TestAttempt::getTestId, Collectors.counting()));
        List<Test> availableTestEntities = groupIds.isEmpty()
                ? List.of()
                : testRepository.findAvailableByTopicIdInForGroups(
                        topicSubjectIds.keySet(),
                        TestStatus.PUBLISHED,
                        groupIds,
                        now
                );
        Map<UUID, TestGroupAvailability> availabilityByTestId = resolveAvailabilityByTestId(
                availableTestEntities,
                groupIds,
                now
        );
        List<StudentTestDashboardItemResponse> availableTests = availableTestEntities.stream()
                .filter(test -> isAvailable(
                        test,
                        availabilityByTestId.get(test.getId()),
                        attemptsByTestId.getOrDefault(test.getId(), 0L)
                ))
                .map(test -> toDashboardItem(
                        test,
                        availabilityByTestId.get(test.getId()),
                        topicSubjectIds.get(test.getTopicId()),
                        attemptsByTestId.getOrDefault(test.getId(), 0L).intValue()
                ))
                .sorted(Comparator.comparing(StudentTestDashboardItemResponse::availableUntil, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StudentTestDashboardItemResponse::title))
                .toList();

        return new StudentTestDashboardResponse(
                availableTests.stream()
                        .filter(item -> item.availableUntil() != null)
                        .limit(DEFAULT_LIST_LIMIT)
                        .toList(),
                availableTests.stream().limit(10).toList()
        );
    }

    @Transactional(readOnly = true)
    public TestingAdminOverviewResponse getAdminOverview() {
        return new TestingAdminOverviewResponse(
                testRepository.countByStatusAndAvailableUntilAfter(TestStatus.PUBLISHED, Instant.now())
        );
    }

    @Transactional(readOnly = true)
    public TeacherTestDashboardResponse getTeacherDashboard(UUID teacherId) {
        List<StudentTestDashboardItemResponse> activeTests = testRepository
                .findAllByCreatedByUserIdAndStatusInOrderByAvailableUntilAscUpdatedAtDesc(
                        teacherId,
                        List.of(TestStatus.DRAFT, TestStatus.PUBLISHED, TestStatus.CLOSED)
                ).stream()
                .limit(DEFAULT_LIST_LIMIT)
                .map(test -> new StudentTestDashboardItemResponse(
                        test.getId(),
                        test.getTopicId(),
                        educationServiceClient.getTopic(test.getTopicId()).subjectId(),
                        test.getTitle(),
                        test.getStatus(),
                        test.getAvailableFrom(),
                        test.getAvailableUntil(),
                        test.getTimeLimitMinutes(),
                        0,
                        test.getMaxAttempts()
                ))
                .toList();
        return new TeacherTestDashboardResponse(activeTests);
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

    private boolean isAvailable(Test test, TestGroupAvailability availability, long attemptsUsed) {
        return availability != null && attemptsUsed < availability.getMaxAttempts();
    }

    private Map<UUID, TestGroupAvailability> resolveAvailabilityByTestId(
            List<Test> tests,
            List<UUID> groupIds,
            Instant now
    ) {
        if (tests.isEmpty() || groupIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TestGroupAvailability> availabilityByTestId = new LinkedHashMap<>();
        testGroupAvailabilityRepository.findAvailableForTestsAndGroups(
                tests.stream().map(Test::getId).toList(),
                groupIds,
                now
        ).forEach(availability -> availabilityByTestId.putIfAbsent(availability.getTestId(), availability));
        return availabilityByTestId;
    }

    private StudentTestDashboardItemResponse toDashboardItem(
            Test test,
            TestGroupAvailability availability,
            UUID subjectId,
            int attemptsUsed
    ) {
        return new StudentTestDashboardItemResponse(
                test.getId(),
                test.getTopicId(),
                subjectId,
                test.getTitle(),
                test.getStatus(),
                availability.getAvailableFrom(),
                availability.getAvailableUntil(),
                test.getTimeLimitMinutes(),
                attemptsUsed,
                availability.getMaxAttempts()
        );
    }
}

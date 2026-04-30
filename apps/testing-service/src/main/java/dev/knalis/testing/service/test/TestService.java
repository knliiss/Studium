package dev.knalis.testing.service.test;

import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.dto.request.CreateTestRequest;
import dev.knalis.testing.dto.request.MoveTestRequest;
import dev.knalis.testing.dto.request.UpsertTestGroupAvailabilityRequest;
import dev.knalis.testing.dto.response.SearchItemResponse;
import dev.knalis.testing.dto.response.SearchPageResponse;
import dev.knalis.testing.dto.response.TestGroupAvailabilityResponse;
import dev.knalis.testing.dto.response.TestPageResponse;
import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestGroupAvailability;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.exception.MaxAttemptsExceededException;
import dev.knalis.testing.exception.TestAccessDeniedException;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.exception.TestNotAvailableException;
import dev.knalis.testing.exception.TestNotFoundException;
import dev.knalis.testing.exception.TestStateTransitionException;
import dev.knalis.testing.factory.attempt.TestAttemptFactory;
import dev.knalis.testing.factory.test.TestFactory;
import dev.knalis.testing.mapper.TestMapper;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestGroupAvailabilityRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.common.TestingAuditService;
import dev.knalis.testing.service.common.TestingEventPublisher;
import dev.knalis.contracts.event.TestPublishedEventV1;
import dev.knalis.contracts.event.TestStartedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "title",
            "availableUntil",
            "availableFrom",
            "orderIndex"
    );
    
    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final TestGroupAvailabilityRepository testGroupAvailabilityRepository;
    private final QuestionRepository questionRepository;
    private final TestFactory testFactory;
    private final TestAttemptFactory testAttemptFactory;
    private final TestMapper testMapper;
    private final TestingAuditService testingAuditService;
    private final TestingEventPublisher testingEventPublisher;
    private final EducationServiceClient educationServiceClient;
    
    @Transactional
    public TestResponse createTest(UUID currentUserId, boolean privilegedAccess, CreateTestRequest request) {
        assertTeacherCanManageTopic(request.topicId(), currentUserId, privilegedAccess);
        Test test = testFactory.newTest(
                request.topicId(),
                request.title(),
                TestStatus.DRAFT,
                request.maxAttempts() == null ? 1 : request.maxAttempts(),
                request.maxPoints() == null ? 100 : request.maxPoints(),
                request.timeLimitMinutes(),
                request.availableFrom(),
                request.availableUntil(),
            Boolean.TRUE.equals(request.showCorrectAnswersAfterSubmit()),
            Boolean.TRUE.equals(request.shuffleQuestions()),
            Boolean.TRUE.equals(request.shuffleAnswers()),
            request.orderIndex() == null ? 0 : request.orderIndex()
        );
        test.setCreatedByUserId(currentUserId);
        TestResponse response = testMapper.toResponse(testRepository.save(test));
        testingAuditService.record(currentUserId, "TEST_CREATED", "TEST", response.id(), null, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public TestResponse getTest(UUID currentUserId, boolean privilegedAccess, boolean teacherAccess, UUID testId) {
        Test test = requireTest(testId);
        if (privilegedAccess || (teacherAccess && canManageTest(test, currentUserId, false))) {
            return testMapper.toResponse(test);
        }
        TestGroupAvailability availability = requireAvailableForStudent(test, currentUserId, Instant.now());
        return toStudentResponse(test, availability);
    }

    @Transactional(readOnly = true)
    public TestPageResponse getTestsByTopic(
            UUID topicId,
            UUID currentUserId,
            int page,
            int size,
            String sortBy,
            String direction,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Test> testPage;
        if (privilegedAccess) {
            testPage = testRepository.findAllByTopicId(topicId, pageRequest);
        } else if (teacherAccess) {
            testPage = canManageTopic(topicId, currentUserId, false)
                    ? testRepository.findAllByTopicId(topicId, pageRequest)
                    : testRepository.findVisibleByTopicIdForTeacher(
                            topicId,
                            currentUserId,
                            TestStatus.PUBLISHED,
                            pageRequest
                    );
        } else {
            Set<UUID> groupIds = resolveStudentGroupIds(currentUserId);
            testPage = groupIds.isEmpty()
                    ? Page.empty(pageRequest)
                    : testRepository.findAvailableByTopicIdForGroups(
                            topicId,
                            TestStatus.PUBLISHED,
                            groupIds,
                            Instant.now(),
                            pageRequest
                    );
        }

        Map<UUID, TestGroupAvailability> studentAvailabilityByTestId = privilegedAccess || teacherAccess
                ? Map.of()
                : resolveAvailabilityByTestId(testPage.getContent(), resolveStudentGroupIds(currentUserId), Instant.now());
        
        return new TestPageResponse(
                testPage.getContent().stream()
                        .map(test -> studentAvailabilityByTestId.containsKey(test.getId())
                                ? toStudentResponse(test, studentAvailabilityByTestId.get(test.getId()))
                                : testMapper.toResponse(test))
                        .toList(),
                testPage.getNumber(),
                testPage.getSize(),
                testPage.getTotalElements(),
                testPage.getTotalPages(),
                testPage.isFirst(),
                testPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SearchPageResponse searchTests(
            String query,
            int page,
            int size,
            String sortBy,
            String direction,
            boolean includeHidden
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        String normalizedQuery = query == null ? "" : query.trim();
        Page<Test> testPage = includeHidden
                ? testRepository.findAllByTitleContainingIgnoreCase(normalizedQuery, pageRequest)
                : testRepository.findAllByTitleContainingIgnoreCaseAndStatus(
                        normalizedQuery,
                        TestStatus.PUBLISHED,
                        pageRequest
                );

        return new SearchPageResponse(
                testPage.getContent().stream().map(this::toSearchItem).toList(),
                testPage.getNumber(),
                testPage.getSize(),
                testPage.getTotalElements(),
                testPage.getTotalPages(),
                testPage.isFirst(),
                testPage.isLast()
        );
    }
    
    @Transactional
    public void startTest(UUID userId, UUID testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException(testId));
        Instant now = Instant.now();
        TestGroupAvailability availability = requireAvailableForStudent(test, userId, now);

        TestAttempt existingActiveAttempt = testAttemptRepository
                .findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId)
                .orElse(null);
        if (existingActiveAttempt != null && !isTimeExpired(test, existingActiveAttempt, now)) {
            return;
        }

        long attemptsCount = testAttemptRepository.countByTestIdAndUserId(testId, userId);
        if (attemptsCount >= availability.getMaxAttempts()) {
            throw new MaxAttemptsExceededException(testId, userId, availability.getMaxAttempts());
        }

        testAttemptRepository.save(testAttemptFactory.newAttempt(testId, userId));
        UUID subjectId = educationServiceClient.getTopic(test.getTopicId()).subjectId();
        testingEventPublisher.publishTestStarted(new TestStartedEventV1(
                UUID.randomUUID(),
                now,
                userId,
                test.getId(),
                subjectId,
                test.getTopicId()
        ));
    }

    @Transactional(readOnly = true)
    public List<TestGroupAvailabilityResponse> getTestAvailability(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID testId
    ) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        return testGroupAvailabilityRepository.findAllByTestIdOrderByCreatedAtAsc(testId)
                .stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    @Transactional
    public TestGroupAvailabilityResponse upsertTestAvailability(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID testId,
            UpsertTestGroupAvailabilityRequest request
    ) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);

        TestGroupAvailability availability = testGroupAvailabilityRepository
                .findByTestIdAndGroupId(testId, request.groupId())
                .orElseGet(TestGroupAvailability::new);
        availability.setTestId(test.getId());
        availability.setGroupId(request.groupId());
        availability.setVisible(Boolean.TRUE.equals(request.visible()));
        availability.setAvailableFrom(request.availableFrom());
        availability.setAvailableUntil(request.availableUntil());
        availability.setDeadline(request.deadline());
        availability.setMaxAttempts(request.maxAttempts() == null ? test.getMaxAttempts() : request.maxAttempts());

        TestGroupAvailability savedAvailability = testGroupAvailabilityRepository.save(availability);
        testingAuditService.record(
                currentUserId,
                "TEST_AVAILABILITY_UPDATED",
                "TEST",
                test.getId(),
                null,
                toAvailabilityResponse(savedAvailability)
        );
        return toAvailabilityResponse(savedAvailability);
    }

    @Transactional
    public TestResponse publishTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        TestResponse oldValue = testMapper.toResponse(test);
        if (test.getStatus() != TestStatus.DRAFT) {
            throw new TestStateTransitionException(testId, test.getStatus(), TestStatus.PUBLISHED);
        }
        if (questionRepository.sumPointsByTestId(testId) > test.getMaxPoints()) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Question points cannot exceed test max points"
            );
        }
        test.setStatus(TestStatus.PUBLISHED);
        Test savedTest = testRepository.save(test);
        testingEventPublisher.publishTestPublished(new TestPublishedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                savedTest.getId(),
                savedTest.getTopicId(),
                savedTest.getTitle(),
                savedTest.getAvailableFrom(),
                savedTest.getAvailableUntil(),
                currentUserId
        ));
        TestResponse response = testMapper.toResponse(savedTest);
        testingAuditService.record(currentUserId, "TEST_PUBLISHED", "TEST", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public TestResponse closeTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        TestResponse oldValue = testMapper.toResponse(test);
        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new TestStateTransitionException(testId, test.getStatus(), TestStatus.CLOSED);
        }
        test.setStatus(TestStatus.CLOSED);
        TestResponse response = testMapper.toResponse(testRepository.save(test));
        testingAuditService.record(currentUserId, "TEST_CLOSED", "TEST", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public TestResponse archiveTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        TestResponse oldValue = testMapper.toResponse(test);
        if (test.getStatus() == TestStatus.ARCHIVED) {
            throw new TestStateTransitionException(testId, test.getStatus(), TestStatus.ARCHIVED);
        }
        test.setStatus(TestStatus.ARCHIVED);
        TestResponse response = testMapper.toResponse(testRepository.save(test));
        testingAuditService.record(currentUserId, "TEST_ARCHIVED", "TEST", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public TestResponse moveTest(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID testId,
            MoveTestRequest request
    ) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        TestResponse oldValue = testMapper.toResponse(test);
        test.setTopicId(request.topicId());
        test.setOrderIndex(request.orderIndex());
        TestResponse response = testMapper.toResponse(testRepository.save(test));
        testingAuditService.record(currentUserId, "TEST_MOVED", "TEST", response.id(), oldValue, response);
        return response;
    }

    private Test requireTest(UUID testId) {
        return testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException(testId));
    }

    private void ensurePublishedAndAvailable(Test test, Instant now) {
        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Only published tests can be started");
        }
        if ((test.getAvailableFrom() != null && now.isBefore(test.getAvailableFrom()))
                || (test.getAvailableUntil() != null && now.isAfter(test.getAvailableUntil()))) {
            throw new TestNotAvailableException(test.getId(), test.getAvailableFrom(), test.getAvailableUntil());
        }
    }

    private TestGroupAvailability requireAvailableForStudent(Test test, UUID userId, Instant now) {
        ensurePublishedAndAvailable(test, now);
        Set<UUID> groupIds = resolveStudentGroupIds(userId);
        if (groupIds.isEmpty()) {
            throw new TestNotAvailableException(test.getId(), test.getAvailableFrom(), test.getAvailableUntil());
        }
        return testGroupAvailabilityRepository.findAvailableForTestAndGroups(test.getId(), groupIds, now)
                .stream()
                .findFirst()
                .orElseThrow(() -> new TestNotAvailableException(
                        test.getId(),
                        test.getAvailableFrom(),
                        test.getAvailableUntil()
                ));
    }

    private boolean isTimeExpired(Test test, TestAttempt attempt, Instant now) {
        return test.getTimeLimitMinutes() != null
                && now.isAfter(attempt.getStartedAt().plusSeconds(test.getTimeLimitMinutes().longValue() * 60L));
    }

    private SearchItemResponse toSearchItem(Test test) {
        return new SearchItemResponse(
                "TEST",
                test.getId(),
                test.getTitle(),
                test.getAvailableUntil() == null ? "Test" : "Available until " + test.getAvailableUntil(),
                Map.of(
                        "testId", test.getId(),
                        "topicId", test.getTopicId(),
                        "status", test.getStatus().name(),
                        "availableUntil", test.getAvailableUntil() == null ? "" : test.getAvailableUntil().toString()
                )
        );
    }

    private Set<UUID> resolveStudentGroupIds(UUID userId) {
        return educationServiceClient.getGroupsByUser(userId).stream()
                .map(groupMembership -> groupMembership.groupId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<UUID, TestGroupAvailability> resolveAvailabilityByTestId(
            List<Test> tests,
            Set<UUID> groupIds,
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

    private TestResponse toStudentResponse(Test test, TestGroupAvailability availability) {
        return new TestResponse(
                test.getId(),
                test.getTopicId(),
                test.getTitle(),
                test.getOrderIndex(),
                test.getStatus(),
                availability.getMaxAttempts(),
                test.getMaxPoints(),
                test.getTimeLimitMinutes(),
                availability.getAvailableFrom(),
                availability.getAvailableUntil(),
                test.isShowCorrectAnswersAfterSubmit(),
                test.isShuffleQuestions(),
                test.isShuffleAnswers(),
                test.getCreatedAt(),
                test.getUpdatedAt()
        );
    }

    private TestGroupAvailabilityResponse toAvailabilityResponse(TestGroupAvailability availability) {
        return new TestGroupAvailabilityResponse(
                availability.getId(),
                availability.getTestId(),
                availability.getGroupId(),
                availability.isVisible(),
                availability.getAvailableFrom(),
                availability.getAvailableUntil(),
                availability.getDeadline(),
                availability.getMaxAttempts(),
                availability.getCreatedAt(),
                availability.getUpdatedAt()
        );
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    public Test requireOwnedTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        return test;
    }

    private void assertTeacherOwnership(Test test, UUID currentUserId, boolean privilegedAccess) {
        if (canManageTest(test, currentUserId, privilegedAccess)) {
            return;
        }
        throw new TestAccessDeniedException(test.getId(), currentUserId);
    }

    private void assertTeacherCanManageTopic(UUID topicId, UUID currentUserId, boolean privilegedAccess) {
        if (canManageTopic(topicId, currentUserId, privilegedAccess)) {
            return;
        }
        throw new TestAccessDeniedException(topicId, currentUserId);
    }

    private boolean canManageTest(Test test, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return true;
        }
        if (test.getCreatedByUserId() != null && test.getCreatedByUserId().equals(currentUserId)) {
            return true;
        }
        return canManageTopic(test.getTopicId(), currentUserId, false);
    }

    private boolean canManageTopic(UUID topicId, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return true;
        }
        UUID subjectId = educationServiceClient.getTopic(topicId).subjectId();
        return isAssignedTeacherForSubject(subjectId, currentUserId);
    }

    private boolean isAssignedTeacherForSubject(UUID subjectId, UUID currentUserId) {
        return educationServiceClient.getSubject(subjectId).teacherIds().contains(currentUserId);
    }
}

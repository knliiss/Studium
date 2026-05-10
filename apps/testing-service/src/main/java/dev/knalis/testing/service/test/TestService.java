package dev.knalis.testing.service.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.dto.request.CreateTestRequest;
import dev.knalis.testing.dto.request.MoveTestRequest;
import dev.knalis.testing.dto.request.UpsertTestGroupAvailabilityRequest;
import dev.knalis.testing.dto.response.SearchItemResponse;
import dev.knalis.testing.dto.response.SearchPageResponse;
import dev.knalis.testing.dto.response.TestGroupAvailabilityResponse;
import dev.knalis.testing.dto.response.TestPageResponse;
import dev.knalis.testing.dto.response.TestPreviewViewResponse;
import dev.knalis.testing.dto.response.TestStudentAnswerOptionResponse;
import dev.knalis.testing.dto.response.TestStudentQuestionViewResponse;
import dev.knalis.testing.dto.response.TestQuestionAnswerResponse;
import dev.knalis.testing.dto.response.TestQuestionViewResponse;
import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.dto.response.TestStudentViewResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestGroupAvailability;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.exception.MaxAttemptsExceededException;
import dev.knalis.testing.exception.TestAccessDeniedException;
import dev.knalis.testing.exception.TestHasAttemptsException;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.exception.TestNotAvailableException;
import dev.knalis.testing.exception.TestNotArchivedException;
import dev.knalis.testing.exception.TestNotFoundException;
import dev.knalis.testing.exception.TestStateTransitionException;
import dev.knalis.testing.factory.attempt.TestAttemptFactory;
import dev.knalis.testing.factory.test.TestFactory;
import dev.knalis.testing.mapper.TestMapper;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestGroupAvailabilityRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.TestResultRepository;
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
import java.util.ArrayList;
import java.util.Collections;
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
    private static final Set<TestStatus> STUDENT_VISIBLE_STATUSES = Set.of(
            TestStatus.PUBLISHED,
            TestStatus.CLOSED
    );
    
    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final TestGroupAvailabilityRepository testGroupAvailabilityRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final TestResultRepository testResultRepository;
    private final TestFactory testFactory;
    private final TestAttemptFactory testAttemptFactory;
    private final TestMapper testMapper;
    private final TestingAuditService testingAuditService;
    private final TestingEventPublisher testingEventPublisher;
    private final EducationServiceClient educationServiceClient;
    private final ObjectMapper objectMapper;
    
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
    public TestPreviewViewResponse getPreviewView(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        return buildPreviewView(testMapper.toResponse(test), test);
    }

    @Transactional(readOnly = true)
    public TestStudentViewResponse getStudentView(UUID currentUserId, UUID testId) {
        Test test = requireTest(testId);
        TestGroupAvailability availability = requireAvailableForStudent(test, currentUserId, Instant.now());
        return buildStudentView(toStudentResponse(test, availability), test);
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
                            STUDENT_VISIBLE_STATUSES,
                            pageRequest
                    );
        } else {
            Set<UUID> groupIds = resolveStudentGroupIds(currentUserId);
            testPage = groupIds.isEmpty()
                    ? Page.empty(pageRequest)
                    : testRepository.findAvailableByTopicIdForGroups(
                            topicId,
                            STUDENT_VISIBLE_STATUSES,
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
    public TestResponse reopenTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        TestResponse oldValue = testMapper.toResponse(test);
        if (test.getStatus() != TestStatus.CLOSED) {
            throw new TestStateTransitionException(testId, test.getStatus(), TestStatus.PUBLISHED);
        }
        test.setStatus(TestStatus.PUBLISHED);
        TestResponse response = testMapper.toResponse(testRepository.save(test));
        testingAuditService.record(currentUserId, "TEST_REOPENED", "TEST", response.id(), oldValue, response);
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
    public TestResponse restoreTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        TestResponse oldValue = testMapper.toResponse(test);
        if (test.getStatus() != TestStatus.ARCHIVED) {
            throw new TestNotArchivedException(testId);
        }
        test.setStatus(TestStatus.DRAFT);
        TestResponse response = testMapper.toResponse(testRepository.save(test));
        testingAuditService.record(currentUserId, "TEST_RESTORED", "TEST", response.id(), oldValue, response);
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

    @Transactional
    public void deleteTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = requireTest(testId);
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        if (test.getStatus() != TestStatus.ARCHIVED) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Only archived tests can be permanently deleted");
        }
        long attemptsCount = testAttemptRepository.countByTestId(testId);
        long resultsCount = testResultRepository.countByTestId(testId);
        if (attemptsCount > 0 || resultsCount > 0) {
            throw new TestHasAttemptsException(testId, attemptsCount, resultsCount);
        }

        List<Question> questions = questionRepository.findAllByTestId(testId);
        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        if (!questionIds.isEmpty()) {
            answerRepository.deleteAllByQuestionIdIn(questionIds);
        }
        questionRepository.deleteAllByTestId(testId);
        testGroupAvailabilityRepository.deleteAllByTestId(testId);
        testAttemptRepository.deleteAllByTestId(testId);
        testResultRepository.deleteAllByTestId(testId);
        testRepository.delete(test);
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

    private TestStudentViewResponse buildStudentView(TestResponse testResponse, Test test) {
        List<Question> questions = questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(test.getId());
        List<UUID> questionIds = questions.stream()
                .map(Question::getId)
                .toList();
        Map<UUID, List<Answer>> answersByQuestionId = questionIds.isEmpty()
                ? Map.of()
                : answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(questionIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                Answer::getQuestionId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<TestStudentQuestionViewResponse> questionResponses = questions.stream()
                .map(question -> toStudentQuestionViewResponse(
                        question,
                        answersByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();

        return new TestStudentViewResponse(
                testResponse,
                questionResponses,
                false,
                test.getMaxPoints(),
                test.getTimeLimitMinutes(),
                Instant.now()
        );
    }

    private TestPreviewViewResponse buildPreviewView(TestResponse testResponse, Test test) {
        List<Question> questions = questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(test.getId());
        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        Map<UUID, List<Answer>> answersByQuestionId = questionIds.isEmpty()
                ? Map.of()
                : answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(questionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Answer::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<TestQuestionViewResponse> questionResponses = questions.stream()
                .map(question -> toPreviewQuestionViewResponse(
                        question,
                        answersByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();
        return new TestPreviewViewResponse(
                testResponse,
                questionResponses,
                true,
                test.getMaxPoints(),
                test.getTimeLimitMinutes(),
                Instant.now()
        );
    }

    private TestStudentQuestionViewResponse toStudentQuestionViewResponse(
            Question question,
            List<Answer> answers
    ) {
        return new TestStudentQuestionViewResponse(
                question.getId(),
                question.getTestId(),
                question.getText(),
                question.getType(),
                question.getDescription(),
                question.getPoints(),
                question.getOrderIndex(),
                question.isRequired(),
                answers.stream()
                        .map(this::toStudentAnswerOptionResponse)
                        .toList(),
                toStudentPresentationJson(question),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    private TestQuestionViewResponse toPreviewQuestionViewResponse(
            Question question,
            List<Answer> answers
    ) {
        return new TestQuestionViewResponse(
                question.getId(),
                question.getTestId(),
                question.getText(),
                question.getType(),
                question.getDescription(),
                question.getPoints(),
                question.getOrderIndex(),
                question.isRequired(),
                question.getFeedback(),
                question.getConfigurationJson(),
                answers.stream()
                        .map(answer -> toPreviewQuestionAnswerResponse(answer))
                        .toList(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    private TestStudentAnswerOptionResponse toStudentAnswerOptionResponse(Answer answer) {
        return new TestStudentAnswerOptionResponse(
                answer.getId(),
                answer.getQuestionId(),
                answer.getText(),
                answer.getCreatedAt(),
                answer.getUpdatedAt()
        );
    }

    private TestQuestionAnswerResponse toPreviewQuestionAnswerResponse(Answer answer) {
        return new TestQuestionAnswerResponse(
                answer.getId(),
                answer.getQuestionId(),
                answer.getText(),
                answer.isCorrect(),
                answer.getCreatedAt(),
                answer.getUpdatedAt()
        );
    }

    private String toStudentPresentationJson(Question question) {
        ObjectNode root = objectMapper.createObjectNode();
        JsonNode config = parseConfiguration(question.getConfigurationJson());
        root.put("type", question.getType().name());
        switch (question.getType()) {
            case SHORT_ANSWER -> {
                if (config.has("maxLength")) {
                    root.set("maxLength", config.get("maxLength"));
                }
            }
            case LONG_TEXT, MANUAL_GRADING -> {
                if (config.has("maxLength")) {
                    root.set("maxLength", config.get("maxLength"));
                }
            }
            case NUMERIC -> {
                if (config.has("unit") && config.get("unit").isTextual()) {
                    root.put("unit", config.get("unit").asText());
                }
            }
            case MATCHING -> {
                ArrayNode leftItems = objectMapper.createArrayNode();
                ArrayNode rightItems = objectMapper.createArrayNode();
                JsonNode leftConfigItems = config.path("leftItems");
                JsonNode rightConfigItems = config.path("rightItems");
                if (leftConfigItems.isArray() && rightConfigItems.isArray()
                        && !leftConfigItems.isEmpty() && !rightConfigItems.isEmpty()) {
                    for (JsonNode item : leftConfigItems) {
                        String id = item.path("id").asText("");
                        String text = item.path("text").asText("");
                        if (!id.isBlank() && !text.isBlank()) {
                            ObjectNode node = objectMapper.createObjectNode();
                            node.put("id", id);
                            node.put("label", text);
                            leftItems.add(node);
                        }
                    }
                    for (JsonNode item : rightConfigItems) {
                        String id = item.path("id").asText("");
                        String text = item.path("text").asText("");
                        if (!id.isBlank() && !text.isBlank()) {
                            ObjectNode node = objectMapper.createObjectNode();
                            node.put("id", id);
                            node.put("label", text);
                            rightItems.add(node);
                        }
                    }
                } else {
                    JsonNode pairs = config.path("pairs");
                    List<String> rightValues = new ArrayList<>();
                    if (pairs.isArray()) {
                        for (int index = 0; index < pairs.size(); index++) {
                            JsonNode pair = pairs.get(index);
                            String left = pair.path("left").asText("");
                            String right = pair.path("right").asText("");
                            if (!left.isBlank() && !right.isBlank()) {
                                ObjectNode leftItem = objectMapper.createObjectNode();
                                leftItem.put("id", String.valueOf(index));
                                leftItem.put("label", left);
                                leftItems.add(leftItem);
                                rightValues.add(right);
                            }
                        }
                    }
                    if (rightValues.size() > 1) {
                        Collections.rotate(rightValues, 1);
                    }
                    for (String right : rightValues) {
                        ObjectNode rightItem = objectMapper.createObjectNode();
                        rightItem.put("id", right);
                        rightItem.put("label", right);
                        rightItems.add(rightItem);
                    }
                }
                root.set("leftItems", leftItems);
                root.set("rightItems", rightItems);
            }
            case ORDERING -> {
                JsonNode items = config.path("items");
                List<ObjectNode> values = new ArrayList<>();
                if (items.isArray()) {
                    items.forEach(node -> {
                        if (node.isObject()) {
                            String id = node.path("id").asText("");
                            String text = node.path("text").asText("");
                            if (!id.isBlank() && !text.isBlank()) {
                                ObjectNode item = objectMapper.createObjectNode();
                                item.put("id", id);
                                item.put("label", text);
                                values.add(item);
                            }
                        } else {
                            String itemText = node.asText("");
                            if (!itemText.isBlank()) {
                                ObjectNode item = objectMapper.createObjectNode();
                                item.put("id", itemText);
                                item.put("label", itemText);
                                values.add(item);
                            }
                        }
                    });
                }
                if (values.size() > 1) {
                    Collections.rotate(values, 1);
                }
                ArrayNode presentationItems = objectMapper.createArrayNode();
                for (ObjectNode value : values) {
                    presentationItems.add(value);
                }
                root.set("items", presentationItems);
            }
            case FILL_IN_THE_BLANK -> {
                String template = config.path("text").asText(question.getText());
                root.put("text", template);
                ArrayNode blanks = objectMapper.createArrayNode();
                JsonNode configBlanks = config.path("blanks");
                if (configBlanks.isArray()) {
                    for (int index = 0; index < configBlanks.size(); index++) {
                        ObjectNode blank = objectMapper.createObjectNode();
                        JsonNode blankConfig = configBlanks.get(index);
                        String id = blankConfig.isObject()
                                ? blankConfig.path("id").asText(String.valueOf(index))
                                : String.valueOf(index);
                        blank.put("id", id);
                        blank.put("placeholder", "Blank " + (index + 1));
                        blanks.add(blank);
                    }
                }
                root.set("blanks", blanks);
            }
            case FILE_ANSWER -> {
                JsonNode allowed = config.path("allowedFileTypes");
                if (allowed.isArray()) {
                    root.set("allowedFileTypes", allowed);
                }
                if (config.has("maxFileSizeMb")) {
                    root.set("maxFileSizeMb", config.get("maxFileSizeMb"));
                }
            }
            case SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE -> {
            }
        }
        return root.toString();
    }

    private JsonNode parseConfiguration(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(configurationJson);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
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

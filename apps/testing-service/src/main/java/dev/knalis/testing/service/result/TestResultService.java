package dev.knalis.testing.service.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.dto.request.CreateTestResultRequest;
import dev.knalis.testing.dto.request.QuestionAnswerSubmissionRequest;
import dev.knalis.testing.dto.request.OverrideTestResultScoreRequest;
import dev.knalis.testing.dto.request.SubmitTestAttemptRequest;
import dev.knalis.testing.dto.response.TestResultPageResponse;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestResult;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.ActiveTestAttemptNotFoundException;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.exception.TestAccessDeniedException;
import dev.knalis.testing.exception.TestNotFoundException;
import dev.knalis.testing.exception.TestNotAvailableException;
import dev.knalis.testing.exception.TestTimeExpiredException;
import dev.knalis.testing.factory.result.TestResultFactory;
import dev.knalis.testing.mapper.TestResultMapper;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.TestResultRepository;
import dev.knalis.testing.service.common.TestingAuditService;
import dev.knalis.testing.service.common.TestingEventPublisher;
import dev.knalis.contracts.event.TestCompletedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestResultService {
    
    private final TestResultRepository testResultRepository;
    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final TestResultFactory testResultFactory;
    private final TestResultMapper testResultMapper;
    private final TestingEventPublisher testingEventPublisher;
    private final EducationServiceClient educationServiceClient;
    private final TestingAuditService testingAuditService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public TestResultResponse createTestResult(UUID userId, CreateTestResultRequest request) {
        Test test = testRepository.findById(request.testId())
                .orElseThrow(() -> new TestNotFoundException(request.testId()));
        Instant now = Instant.now();
        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Only published tests can be submitted");
        }
        if (test.getAvailableUntil() != null && now.isAfter(test.getAvailableUntil())) {
            throw new TestNotAvailableException(test.getId(), test.getAvailableFrom(), test.getAvailableUntil());
        }
        if (request.score() > test.getMaxPoints()) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Result score cannot exceed test max points");
        }
        TestAttempt attempt = testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(
                        request.testId(),
                        userId
                )
                .orElseThrow(() -> new TestInvalidStateException(
                        test.getId(),
                        test.getStatus(),
                        "Test result submission requires an active test attempt"
                ));
        if (test.getTimeLimitMinutes() != null
                && now.isAfter(attempt.getStartedAt().plusSeconds(test.getTimeLimitMinutes().longValue() * 60L))) {
            attempt.setCompletedAt(now);
            testAttemptRepository.save(attempt);
            throw new TestTimeExpiredException(test.getId(), attempt.getId(), attempt.getStartedAt(), test.getTimeLimitMinutes());
        }
        
        TestResult testResult = testResultFactory.newTestResult(request.testId(), userId, attempt.getId(), request.score());
        TestResult savedTestResult = testResultRepository.save(testResult);
        attempt.setCompletedAt(now);
        testAttemptRepository.save(attempt);
        UUID subjectId = educationServiceClient.getTopic(test.getTopicId()).subjectId();
        testingEventPublisher.publishTestCompleted(new TestCompletedEventV1(
                UUID.randomUUID(),
                savedTestResult.getCreatedAt(),
                userId,
                test.getId(),
                subjectId,
                test.getTopicId(),
                savedTestResult.getScore(),
                test.getMaxPoints(),
                savedTestResult.getCreatedAt()
        ));
        return testResultMapper.toResponse(savedTestResult);
    }

    @Transactional
    public TestResultResponse submitTestAttempt(
            UUID userId,
            UUID testId,
            SubmitTestAttemptRequest request
    ) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException(testId));
        Instant now = Instant.now();
        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Only published tests can be submitted");
        }
        if (test.getAvailableUntil() != null && now.isAfter(test.getAvailableUntil())) {
            throw new TestNotAvailableException(test.getId(), test.getAvailableFrom(), test.getAvailableUntil());
        }

        TestAttempt attempt = testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId)
                .orElseGet(() -> recoverCompletedAttemptWithoutResult(testId, userId).orElseThrow(
                        () -> new ActiveTestAttemptNotFoundException(testId, userId)
                ));

        TestResult existingResult = testResultRepository.findFirstByAttemptId(attempt.getId()).orElse(null);
        if (existingResult != null) {
            return testResultMapper.toResponse(existingResult);
        }

        int score = calculateScore(testId, request);
        TestResult testResult = testResultFactory.newTestResult(testId, userId, attempt.getId(), Math.min(score, test.getMaxPoints()));
        TestResult savedTestResult = testResultRepository.save(testResult);
        Instant completedAt = resolveCompletedAt(test, attempt, now);
        attempt.setCompletedAt(completedAt);
        testAttemptRepository.save(attempt);
        UUID subjectId = educationServiceClient.getTopic(test.getTopicId()).subjectId();
        testingEventPublisher.publishTestCompleted(new TestCompletedEventV1(
                UUID.randomUUID(),
                savedTestResult.getCreatedAt(),
                userId,
                test.getId(),
                subjectId,
                test.getTopicId(),
                savedTestResult.getScore(),
                test.getMaxPoints(),
                savedTestResult.getCreatedAt()
        ));
        return testResultMapper.toResponse(savedTestResult);
    }

    private Optional<TestAttempt> recoverCompletedAttemptWithoutResult(UUID testId, UUID userId) {
        return testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(testId, userId)
                .filter(attempt -> testResultRepository.findFirstByAttemptId(attempt.getId()).isEmpty());
    }

    private Instant resolveCompletedAt(Test test, TestAttempt attempt, Instant now) {
        if (test.getTimeLimitMinutes() == null) {
            return now;
        }
        Instant deadline = attempt.getStartedAt().plusSeconds(test.getTimeLimitMinutes().longValue() * 60L);
        return now.isAfter(deadline) ? deadline : now;
    }

    @Transactional(readOnly = true)
    public TestResultResponse getTestResult(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID resultId
    ) {
        TestResult testResult = testResultRepository.findById(resultId)
                .orElseThrow(() -> new TestNotFoundException(resultId));
        Test test = testRepository.findById(testResult.getTestId())
                .orElseThrow(() -> new TestNotFoundException(testResult.getTestId()));
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        return testResultMapper.toResponse(testResult);
    }

    @Transactional(readOnly = true)
    public TestResultPageResponse getTestResultsByTest(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID testId,
            int page,
            int size
    ) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException(testId));
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<TestResult> resultsPage = testResultRepository.findAllByTestId(testId, pageRequest);
        return new TestResultPageResponse(
                resultsPage.getContent().stream().map(testResultMapper::toResponse).toList(),
                resultsPage.getNumber(),
                resultsPage.getSize(),
                resultsPage.getTotalElements(),
                resultsPage.getTotalPages(),
                resultsPage.isFirst(),
                resultsPage.isLast()
        );
    }

    @Transactional
    public TestResultResponse overrideTestResultScore(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID resultId,
            OverrideTestResultScoreRequest request
    ) {
        TestResult testResult = testResultRepository.findById(resultId)
                .orElseThrow(() -> new TestNotFoundException(resultId));
        Test test = testRepository.findById(testResult.getTestId())
                .orElseThrow(() -> new TestNotFoundException(testResult.getTestId()));
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        if (request.score() > test.getMaxPoints()) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Override score cannot exceed test max points");
        }
        TestResultResponse oldValue = testResultMapper.toResponse(testResult);

        testResult.setScore(request.score());
        testResult.setManualOverrideScore(request.score());
        testResult.setManualOverrideReason(request.reason() == null || request.reason().isBlank()
                ? null
                : request.reason().trim());
        testResult.setReviewedByUserId(currentUserId);
        testResult.setReviewedAt(Instant.now());

        TestResult savedTestResult = testResultRepository.save(testResult);
        TestResultResponse response = testResultMapper.toResponse(savedTestResult);
        testingAuditService.record(currentUserId, "TEST_RESULT_SCORE_OVERRIDDEN", "TEST_RESULT", response.id(), oldValue, response);
        return response;
    }

    private void assertTeacherOwnership(Test test, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return;
        }
        if (test.getCreatedByUserId() != null && test.getCreatedByUserId().equals(currentUserId)) {
            return;
        }
        UUID subjectId = educationServiceClient.getTopic(test.getTopicId()).subjectId();
        if (educationServiceClient.getSubject(subjectId).teacherIds().contains(currentUserId)) {
            return;
        }
        throw new TestAccessDeniedException(test.getId(), currentUserId);
    }

    private int calculateScore(UUID testId, SubmitTestAttemptRequest request) {
        List<Question> questions = questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId);
        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        Map<UUID, List<Answer>> answersByQuestionId = questionIds.isEmpty()
                ? Map.of()
                : answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(questionIds).stream()
                        .collect(Collectors.groupingBy(
                                Answer::getQuestionId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        Map<UUID, JsonNode> submissionsByQuestionId = request.answers() == null
                ? Map.of()
                : request.answers().stream()
                        .collect(Collectors.toMap(
                                QuestionAnswerSubmissionRequest::questionId,
                                QuestionAnswerSubmissionRequest::value,
                                (left, right) -> right,
                                HashMap::new
                        ));

        int total = 0;
        for (Question question : questions) {
            JsonNode submittedValue = submissionsByQuestionId.get(question.getId());
            if (submittedValue == null) {
                continue;
            }
            if (isCorrectAnswer(question, submittedValue, answersByQuestionId.getOrDefault(question.getId(), List.of()))) {
                total += question.getPoints();
            }
        }
        return total;
    }

    private boolean isCorrectAnswer(Question question, JsonNode submittedValue, List<Answer> answers) {
        return switch (question.getType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> isCorrectSingleChoice(submittedValue, answers);
            case MULTIPLE_CHOICE -> isCorrectMultipleChoice(submittedValue, answers);
            case SHORT_ANSWER -> isCorrectShortAnswer(submittedValue, answers);
            case NUMERIC -> isCorrectNumeric(question, submittedValue, answers);
            case MATCHING -> isCorrectMatching(question, submittedValue);
            case ORDERING -> isCorrectOrdering(question, submittedValue);
            case FILL_IN_THE_BLANK -> isCorrectFillInBlank(question, submittedValue);
            case LONG_TEXT, FILE_ANSWER, MANUAL_GRADING -> false;
        };
    }

    private boolean isCorrectSingleChoice(JsonNode submittedValue, List<Answer> answers) {
        if (!submittedValue.isTextual()) {
            return false;
        }
        String selectedAnswerId = submittedValue.asText();
        return answers.stream()
                .anyMatch(answer -> answer.isCorrect() && answer.getId().toString().equals(selectedAnswerId));
    }

    private boolean isCorrectMultipleChoice(JsonNode submittedValue, List<Answer> answers) {
        if (!submittedValue.isArray()) {
            return false;
        }
        Set<String> selectedIds = new HashSet<>();
        submittedValue.forEach(node -> {
            if (node.isTextual()) {
                selectedIds.add(node.asText());
            }
        });
        Set<String> correctIds = answers.stream()
                .filter(Answer::isCorrect)
                .map(answer -> answer.getId().toString())
                .collect(Collectors.toCollection(HashSet::new));
        return correctIds.equals(selectedIds);
    }

    private boolean isCorrectShortAnswer(JsonNode submittedValue, List<Answer> answers) {
        if (!submittedValue.isTextual()) {
            return false;
        }
        String normalizedAnswer = submittedValue.asText("").trim().toLowerCase();
        return !normalizedAnswer.isEmpty() && answers.stream()
                .map(Answer::getText)
                .map(value -> value == null ? "" : value.trim().toLowerCase())
                .anyMatch(normalizedAnswer::equals);
    }

    private boolean isCorrectNumeric(Question question, JsonNode submittedValue, List<Answer> answers) {
        if (!submittedValue.isTextual() && !submittedValue.isNumber()) {
            return false;
        }
        JsonNode config = parseConfiguration(question);
        double tolerance = config.path("tolerance").asDouble(0d);
        double correctValue = config.has("correctValue")
                ? config.path("correctValue").asDouble(Double.NaN)
                : parseDoubleSafe(answers.isEmpty() ? null : answers.get(0).getText());
        double studentValue = submittedValue.asDouble(Double.NaN);
        return Double.isFinite(correctValue)
                && Double.isFinite(studentValue)
                && Math.abs(studentValue - correctValue) <= tolerance;
    }

    private boolean isCorrectMatching(Question question, JsonNode submittedValue) {
        if (!submittedValue.isObject()) {
            return false;
        }
        JsonNode config = parseConfiguration(question);
        JsonNode pairs = config.path("pairs");
        if (!pairs.isArray() || pairs.isEmpty()) {
            return false;
        }
        boolean idBased = pairs.get(0).has("leftId") && pairs.get(0).has("rightId");
        if (idBased) {
            for (int index = 0; index < pairs.size(); index++) {
                JsonNode pair = pairs.get(index);
                String leftId = pair.path("leftId").asText(null);
                String rightId = pair.path("rightId").asText(null);
                if (leftId == null || rightId == null) {
                    return false;
                }
                String actualRight = submittedValue.path(leftId).asText(null);
                if (actualRight == null || !rightId.equals(actualRight)) {
                    return false;
                }
            }
            return true;
        }
        for (int index = 0; index < pairs.size(); index++) {
            JsonNode pair = pairs.get(index);
            String expectedRight = pair.path("right").asText(null);
            String actualRight = submittedValue.path(String.valueOf(index)).asText(null);
            if (expectedRight == null || actualRight == null || !expectedRight.equals(actualRight)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCorrectOrdering(Question question, JsonNode submittedValue) {
        if (!submittedValue.isArray()) {
            return false;
        }
        JsonNode config = parseConfiguration(question);
        JsonNode items = config.path("items");
        if (!items.isArray() || items.isEmpty() || items.size() != submittedValue.size()) {
            return false;
        }
        boolean idBased = items.get(0).isObject() && items.get(0).has("id");
        for (int index = 0; index < items.size(); index++) {
            String expected = idBased
                    ? items.get(index).path("id").asText(null)
                    : items.get(index).asText(null);
            String actual = submittedValue.get(index).asText(null);
            if (expected == null || actual == null || !expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCorrectFillInBlank(Question question, JsonNode submittedValue) {
        JsonNode config = parseConfiguration(question);
        JsonNode blanks = config.path("blanks");
        if (!blanks.isArray() || blanks.isEmpty()) {
            return false;
        }
        if (submittedValue.isObject()) {
            for (int index = 0; index < blanks.size(); index++) {
                JsonNode blank = blanks.get(index);
                String blankId = blank.isObject() ? blank.path("id").asText(String.valueOf(index)) : String.valueOf(index);
                JsonNode accepted = blank.isObject() ? blank.path("acceptedAnswers") : blank;
                if (!accepted.isArray()) {
                    return false;
                }
                String submitted = submittedValue.path(blankId).asText(null);
                if (submitted == null) {
                    return false;
                }
                if (!matchesAcceptedAnswer(submitted, accepted)) {
                    return false;
                }
            }
            return true;
        }
        if (!submittedValue.isTextual()) {
            return false;
        }
        List<String> submittedValues = splitLines(submittedValue.asText());
        if (blanks.size() != submittedValues.size()) {
            return false;
        }
        for (int index = 0; index < blanks.size(); index++) {
            JsonNode blank = blanks.get(index);
            JsonNode accepted = blank.isObject() ? blank.path("acceptedAnswers") : blank;
            if (!accepted.isArray()) {
                return false;
            }
            String submitted = submittedValues.get(index);
            if (!matchesAcceptedAnswer(submitted, accepted)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAcceptedAnswer(String submitted, JsonNode accepted) {
        String normalized = submitted.trim().toLowerCase();
        for (JsonNode node : accepted) {
            if (normalized.equals(node.asText("").trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode parseConfiguration(Question question) {
        if (question.getConfigurationJson() == null || question.getConfigurationJson().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(question.getConfigurationJson());
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private double parseDoubleSafe(String value) {
        if (value == null || value.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }

    private List<String> splitLines(String value) {
        return value.lines()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
    }
}

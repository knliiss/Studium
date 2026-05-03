package dev.knalis.testing.service.question;

import dev.knalis.testing.dto.request.CreateQuestionRequest;
import dev.knalis.testing.dto.request.QuestionAnswerDraftRequest;
import dev.knalis.testing.dto.request.UpdateQuestionRequest;
import dev.knalis.testing.dto.response.AnswerResponse;
import dev.knalis.testing.dto.response.QuestionResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.QuestionNotFoundException;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.factory.answer.AnswerFactory;
import dev.knalis.testing.factory.question.QuestionFactory;
import dev.knalis.testing.mapper.AnswerMapper;
import dev.knalis.testing.mapper.QuestionMapper;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.test.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService {
    
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AnswerFactory answerFactory;
    private final QuestionFactory questionFactory;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final TestService testService;
    
    @Transactional
    public QuestionResponse createQuestion(UUID currentUserId, boolean privilegedAccess, CreateQuestionRequest request) {
        Test test = testService.requireOwnedTest(currentUserId, privilegedAccess, request.testId());
        if (test.getStatus() != TestStatus.DRAFT) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Structural editing is locked after publishing"
            );
        }
        int points = request.points() == null ? 1 : request.points();
        int usedPoints = questionRepository.sumPointsByTestId(request.testId());
        if (usedPoints + points > test.getMaxPoints()) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Question points cannot exceed test max points"
            );
        }
        Question question = questionFactory.newQuestion(
                request.testId(),
                request.text(),
                request.type() == null ? QuestionType.SINGLE_CHOICE : request.type(),
                request.description(),
                points,
                request.orderIndex() == null ? 0 : request.orderIndex(),
                !Boolean.FALSE.equals(request.required()),
                request.feedback(),
                request.configurationJson()
        );
        return toResponse(questionRepository.save(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> getQuestionsByTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        testService.requireOwnedTest(currentUserId, privilegedAccess, testId);
        return questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public QuestionResponse updateQuestion(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID questionId,
            UpdateQuestionRequest request
    ) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        Test test = requireDraftOwnedTest(currentUserId, privilegedAccess, question.getTestId());

        int nextPoints = request.points() == null ? 1 : request.points();
        int usedPointsWithoutCurrent = questionRepository.sumPointsByTestId(test.getId()) - question.getPoints();
        if (usedPointsWithoutCurrent + nextPoints > test.getMaxPoints()) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Question points cannot exceed test max points"
            );
        }

        question.setText(request.text().trim());
        question.setType(request.type() == null ? QuestionType.SINGLE_CHOICE : request.type());
        question.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        question.setPoints(nextPoints);
        question.setOrderIndex(request.orderIndex() == null ? question.getOrderIndex() : request.orderIndex());
        question.setRequired(!Boolean.FALSE.equals(request.required()));
        question.setFeedback(request.feedback() == null || request.feedback().isBlank() ? null : request.feedback().trim());
        question.setConfigurationJson(request.configurationJson() == null || request.configurationJson().isBlank()
                ? null
                : request.configurationJson().trim());

        Question savedQuestion = questionRepository.save(question);
        replaceAnswers(savedQuestion, request.answers());
        return toResponse(savedQuestion);
    }

    @Transactional
    public void deleteQuestion(UUID currentUserId, boolean privilegedAccess, UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        requireDraftOwnedTest(currentUserId, privilegedAccess, question.getTestId());
        answerRepository.deleteAllByQuestionId(questionId);
        questionRepository.delete(question);
    }

    private Test requireDraftOwnedTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        Test test = testService.requireOwnedTest(currentUserId, privilegedAccess, testId);
        if (test.getStatus() != TestStatus.DRAFT) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Structural editing is locked after publishing"
            );
        }
        return test;
    }

    private void replaceAnswers(Question question, List<QuestionAnswerDraftRequest> answers) {
        answerRepository.deleteAllByQuestionId(question.getId());
        if (answers == null || answers.isEmpty()) {
            return;
        }

        List<Answer> nextAnswers = new ArrayList<>();
        for (QuestionAnswerDraftRequest answerDraft : answers) {
            if (answerDraft.text() == null || answerDraft.text().isBlank()) {
                continue;
            }
            nextAnswers.add(answerFactory.newAnswer(
                    question.getId(),
                    answerDraft.text(),
                    Boolean.TRUE.equals(answerDraft.isCorrect())
            ));
        }
        if (!nextAnswers.isEmpty()) {
            answerRepository.saveAll(nextAnswers);
        }
    }

    private QuestionResponse toResponse(Question question) {
        List<AnswerResponse> answers = answerRepository.findAllByQuestionIdOrderByCreatedAtAsc(question.getId()).stream()
                .map(answerMapper::toResponse)
                .toList();
        return new QuestionResponse(
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
                answers,
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }
}

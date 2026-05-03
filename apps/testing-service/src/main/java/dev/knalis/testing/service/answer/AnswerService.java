package dev.knalis.testing.service.answer;

import dev.knalis.testing.dto.request.CreateAnswerRequest;
import dev.knalis.testing.dto.response.AnswerResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.QuestionNotFoundException;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.factory.answer.AnswerFactory;
import dev.knalis.testing.mapper.AnswerMapper;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.test.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnswerService {
    
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerFactory answerFactory;
    private final AnswerMapper answerMapper;
    private final TestService testService;
    
    @Transactional
    public AnswerResponse createAnswer(UUID currentUserId, boolean privilegedAccess, CreateAnswerRequest request) {
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new QuestionNotFoundException(request.questionId()));
        Test test = testService.requireOwnedTest(currentUserId, privilegedAccess, question.getTestId());
        if (test.getStatus() != TestStatus.DRAFT) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Structural editing is locked after publishing"
            );
        }
        if (Boolean.TRUE.equals(request.isCorrect())
                && requiresSingleCorrectAnswer(question.getType())
                && answerRepository.existsByQuestionIdAndCorrectTrue(question.getId())) {
            throw new TestInvalidStateException(
                    test.getId(),
                    test.getStatus(),
                    "Only one correct answer is allowed for this question type"
            );
        }
        Answer answer = answerFactory.newAnswer(request.questionId(), request.text(), request.isCorrect());
        return answerMapper.toResponse(answerRepository.save(answer));
    }

    @Transactional(readOnly = true)
    public List<AnswerResponse> getAnswersByQuestion(UUID currentUserId, boolean privilegedAccess, UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        testService.requireOwnedTest(currentUserId, privilegedAccess, question.getTestId());
        return answerRepository.findAllByQuestionIdOrderByCreatedAtAsc(questionId)
                .stream()
                .map(answerMapper::toResponse)
                .toList();
    }

    private boolean requiresSingleCorrectAnswer(QuestionType type) {
        return type == QuestionType.SINGLE_CHOICE || type == QuestionType.TRUE_FALSE;
    }
}

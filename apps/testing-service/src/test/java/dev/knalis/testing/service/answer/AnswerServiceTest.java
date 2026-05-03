package dev.knalis.testing.service.answer;

import dev.knalis.testing.dto.request.CreateAnswerRequest;
import dev.knalis.testing.dto.response.AnswerResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.QuestionNotFoundException;
import dev.knalis.testing.factory.answer.AnswerFactory;
import dev.knalis.testing.mapper.AnswerMapper;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.test.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {
    
    @Mock
    private AnswerRepository answerRepository;
    
    @Mock
    private QuestionRepository questionRepository;
    
    @Mock
    private AnswerMapper answerMapper;

    @Mock
    private TestService testService;
    
    private AnswerService answerService;
    
    @BeforeEach
    void setUp() {
        answerService = new AnswerService(
                answerRepository,
                questionRepository,
                new AnswerFactory(),
                answerMapper,
                testService
        );
    }
    
    @Test
    void createAnswerThrowsWhenQuestionIsMissing() {
        UUID questionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());
        
        assertThrows(
                QuestionNotFoundException.class,
                () -> answerService.createAnswer(actorId, false, new CreateAnswerRequest(questionId, "Answer", true))
        );
    }
    
    @Test
    void createAnswerSavesAnswer() {
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        Instant now = Instant.now();

        Question question = new Question();
        question.setId(questionId);
        question.setTestId(testId);

        dev.knalis.testing.entity.Test test = new dev.knalis.testing.entity.Test();
        test.setId(testId);
        test.setStatus(TestStatus.DRAFT);
        
        Answer answer = new Answer();
        answer.setId(answerId);
        answer.setQuestionId(questionId);
        answer.setText("Answer");
        answer.setCorrect(true);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        
        AnswerResponse response = new AnswerResponse(answerId, questionId, "Answer", true, now, now);
        
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(testService.requireOwnedTest(actorId, false, testId)).thenReturn(test);
        when(answerRepository.existsByQuestionIdAndCorrectTrue(questionId)).thenReturn(false);
        when(answerRepository.save(any(Answer.class))).thenReturn(answer);
        when(answerMapper.toResponse(answer)).thenReturn(response);
        
        AnswerResponse result = answerService.createAnswer(actorId, false, new CreateAnswerRequest(questionId, "Answer", true));
        
        assertEquals(response, result);
    }
}

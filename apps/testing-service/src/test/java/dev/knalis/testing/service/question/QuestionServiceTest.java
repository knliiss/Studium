package dev.knalis.testing.service.question;

import dev.knalis.testing.dto.request.CreateQuestionRequest;
import dev.knalis.testing.dto.response.QuestionResponse;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.exception.TestNotFoundException;
import dev.knalis.testing.factory.question.QuestionFactory;
import dev.knalis.testing.mapper.QuestionMapper;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.test.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {
    
    @Mock
    private QuestionRepository questionRepository;
    
    @Mock
    private TestService testService;
    
    @Mock
    private QuestionMapper questionMapper;
    
    private QuestionService questionService;
    
    @BeforeEach
    void setUp() {
        questionService = new QuestionService(
                questionRepository,
                new QuestionFactory(),
                questionMapper,
                testService
        );
    }
    
    @Test
    void createQuestionThrowsWhenTestIsMissing() {
        UUID testId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        when(testService.requireOwnedTest(actorId, false, testId)).thenThrow(new TestNotFoundException(testId));
        
        assertThrows(
                TestNotFoundException.class,
                () -> questionService.createQuestion(actorId, false, new CreateQuestionRequest(
                        testId,
                        "Question?",
                        QuestionType.SINGLE_CHOICE,
                        null,
                        1,
                        0,
                        true,
                        null
                ))
        );
    }
    
    @Test
    void createQuestionSavesQuestion() {
        UUID testId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Question question = new Question();
        question.setId(questionId);
        question.setTestId(testId);
        question.setText("Question?");
        question.setType(QuestionType.SINGLE_CHOICE);
        question.setPoints(1);
        question.setOrderIndex(0);
        question.setRequired(true);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        
        QuestionResponse response = new QuestionResponse(
                questionId,
                testId,
                "Question?",
                QuestionType.SINGLE_CHOICE,
                null,
                1,
                0,
                true,
                null,
                now,
                now
        );
        
        when(testService.requireOwnedTest(actorId, false, testId)).thenReturn(null);
        when(questionRepository.save(any(Question.class))).thenReturn(question);
        when(questionMapper.toResponse(question)).thenReturn(response);
        
        QuestionResponse result = questionService.createQuestion(actorId, false, new CreateQuestionRequest(
                testId,
                "Question?",
                QuestionType.SINGLE_CHOICE,
                null,
                1,
                0,
                true,
                null
        ));
        
        assertEquals(response, result);
    }
}

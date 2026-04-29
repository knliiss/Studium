package dev.knalis.testing.service.question;

import dev.knalis.testing.dto.request.CreateQuestionRequest;
import dev.knalis.testing.dto.response.QuestionResponse;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.factory.question.QuestionFactory;
import dev.knalis.testing.mapper.QuestionMapper;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.test.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService {
    
    private final QuestionRepository questionRepository;
    private final QuestionFactory questionFactory;
    private final QuestionMapper questionMapper;
    private final TestService testService;
    
    @Transactional
    public QuestionResponse createQuestion(UUID currentUserId, boolean privilegedAccess, CreateQuestionRequest request) {
        testService.requireOwnedTest(currentUserId, privilegedAccess, request.testId());
        Question question = questionFactory.newQuestion(
                request.testId(),
                request.text(),
                request.type() == null ? QuestionType.SINGLE_CHOICE : request.type(),
                request.description(),
                request.points() == null ? 1 : request.points(),
                request.orderIndex() == null ? 0 : request.orderIndex(),
                !Boolean.FALSE.equals(request.required()),
                request.feedback()
        );
        return questionMapper.toResponse(questionRepository.save(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> getQuestionsByTest(UUID currentUserId, boolean privilegedAccess, UUID testId) {
        testService.requireOwnedTest(currentUserId, privilegedAccess, testId);
        return questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId).stream()
                .map(questionMapper::toResponse)
                .toList();
    }
}

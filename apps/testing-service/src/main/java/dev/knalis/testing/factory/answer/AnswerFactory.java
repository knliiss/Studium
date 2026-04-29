package dev.knalis.testing.factory.answer;

import dev.knalis.testing.entity.Answer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AnswerFactory {
    
    public Answer newAnswer(UUID questionId, String text, boolean isCorrect) {
        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setText(text.trim());
        answer.setCorrect(isCorrect);
        return answer;
    }
}

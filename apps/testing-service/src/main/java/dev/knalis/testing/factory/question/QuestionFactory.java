package dev.knalis.testing.factory.question;

import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class QuestionFactory {
    
    public Question newQuestion(
            UUID testId,
            String text,
            QuestionType type,
            String description,
            int points,
            int orderIndex,
            boolean required,
            String feedback
    ) {
        Question question = new Question();
        question.setTestId(testId);
        question.setText(text.trim());
        question.setType(type);
        question.setDescription(description == null || description.isBlank() ? null : description.trim());
        question.setPoints(points);
        question.setOrderIndex(orderIndex);
        question.setRequired(required);
        question.setFeedback(feedback == null || feedback.isBlank() ? null : feedback.trim());
        return question;
    }
}

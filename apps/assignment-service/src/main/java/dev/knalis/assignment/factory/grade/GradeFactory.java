package dev.knalis.assignment.factory.grade;

import dev.knalis.assignment.entity.Grade;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GradeFactory {
    
    public Grade newGrade(UUID submissionId, int score, String feedback) {
        Grade grade = new Grade();
        grade.setSubmissionId(submissionId);
        grade.setScore(score);
        grade.setFeedback(feedback == null || feedback.isBlank() ? null : feedback.trim());
        return grade;
    }
}

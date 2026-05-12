package dev.knalis.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "test_result_questions",
        indexes = {
                @Index(name = "idx_test_result_questions_result_id", columnList = "resultId"),
                @Index(name = "idx_test_result_questions_question_id", columnList = "questionId")
        }
)
public class TestResultQuestion {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID resultId;

    @Column(nullable = false)
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuestionType questionType;

    @Column(nullable = false, length = 2000)
    private String questionText;

    @Column(nullable = false)
    private int questionOrderIndex;

    @Column(nullable = false)
    private int maxPoints;

    @Column(columnDefinition = "text")
    private String submittedValueJson;

    @Column(columnDefinition = "text")
    private String correctValueJson;

    @Column(nullable = false)
    private int autoScore;

    @Column(nullable = false)
    private int score;

    @Column(length = 2000)
    private String reviewComment;

    @Column
    private UUID reviewedByUserId;

    @Column
    private Instant reviewedAt;

    @Column
    private Integer timeSpentSeconds;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

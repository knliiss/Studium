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
        name = "questions",
        indexes = {
                @Index(name = "idx_questions_test_id", columnList = "testId")
        }
)
public class Question {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID testId;
    
    @Column(nullable = false, length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuestionType type = QuestionType.SINGLE_CHOICE;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private int points = 1;

    @Column(nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private boolean required = true;

    @Column(length = 2000)
    private String feedback;
    
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

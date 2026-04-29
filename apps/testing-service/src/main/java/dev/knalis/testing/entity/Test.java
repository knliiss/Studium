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
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "tests",
        indexes = {
                @Index(name = "idx_tests_topic_id", columnList = "topicId"),
                @Index(name = "idx_tests_status", columnList = "status"),
                @Index(name = "idx_tests_created_by_user_id", columnList = "createdByUserId")
        }
)
public class Test {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID topicId;

    @Column
    private UUID createdByUserId;
    
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestStatus status = TestStatus.DRAFT;

    @Column(nullable = false)
    private int maxAttempts = 1;

    @Column(nullable = false)
    private int maxPoints = 100;

    @Column
    private Integer timeLimitMinutes;

    @Column
    private Instant availableFrom;

    @Column
    private Instant availableUntil;

    @Column(nullable = false)
    private boolean showCorrectAnswersAfterSubmit;

    @Column(nullable = false)
    private boolean shuffleQuestions;

    @Column(nullable = false)
    private boolean shuffleAnswers;
    
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

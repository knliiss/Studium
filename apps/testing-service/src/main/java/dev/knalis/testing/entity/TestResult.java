package dev.knalis.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "test_results",
        indexes = {
                @Index(name = "idx_test_results_test_id", columnList = "testId"),
                @Index(name = "idx_test_results_user_id", columnList = "userId")
        }
)
public class TestResult {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID testId;
    
    @Column(nullable = false)
    private UUID userId;

    @Column
    private UUID attemptId;
    
    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int autoScore;

    @Column
    private Integer manualOverrideScore;

    @Column(length = 1000)
    private String manualOverrideReason;

    @Column
    private UUID reviewedByUserId;

    @Column
    private Instant reviewedAt;
    
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

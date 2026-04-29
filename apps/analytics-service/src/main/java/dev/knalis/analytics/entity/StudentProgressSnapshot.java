package dev.knalis.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "student_progress_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_progress_user_group", columnNames = {"userId", "groupId"})
        },
        indexes = {
                @Index(name = "idx_student_progress_user_id", columnList = "userId"),
                @Index(name = "idx_student_progress_group_id", columnList = "groupId"),
                @Index(name = "idx_student_progress_risk_level", columnList = "riskLevel")
        }
)
public class StudentProgressSnapshot {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column
    private UUID groupId;
    
    @Column
    private Double averageScore;
    
    @Column(nullable = false)
    private int assignmentsCreatedCount;
    
    @Column(nullable = false)
    private int assignmentsSubmittedCount;
    
    @Column(nullable = false)
    private int assignmentsLateCount;
    
    @Column(nullable = false)
    private int testsCompletedCount;
    
    @Column(nullable = false)
    private int missedDeadlinesCount;
    
    @Column(nullable = false)
    private int lectureOpenCount;
    
    @Column(nullable = false)
    private int topicOpenCount;
    
    @Column
    private Instant lastActivityAt;
    
    @Column(nullable = false)
    private int activityScore;
    
    @Column(nullable = false)
    private int disciplineScore = 100;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.LOW;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformanceTrend performanceTrend = PerformanceTrend.UNKNOWN;
    
    @Column(nullable = false)
    private int assignmentOpenedCount;
    
    @Column(nullable = false)
    private int testStartedCount;
    
    @Column(nullable = false)
    private double scoreTotal;
    
    @Column(nullable = false)
    private int scoreCount;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

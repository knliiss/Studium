package dev.knalis.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "subject_analytics_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subject_analytics_subject_group", columnNames = {"subjectId", "groupId"})
        },
        indexes = {
                @Index(name = "idx_subject_analytics_subject_id", columnList = "subjectId"),
                @Index(name = "idx_subject_analytics_group_id", columnList = "groupId")
        }
)
public class SubjectAnalyticsSnapshot {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID subjectId;
    
    @Column
    private UUID groupId;
    
    @Column
    private Double averageScore;
    
    @Column(nullable = false)
    private double completionRate;
    
    @Column(nullable = false)
    private double lateSubmissionRate;
    
    @Column(nullable = false)
    private double missedDeadlineRate;
    
    @Column(nullable = false)
    private long activeStudentsCount;
    
    @Column(nullable = false)
    private long atRiskStudentsCount;
    
    @Column(nullable = false)
    private int lectureOpenCount;
    
    @Column(nullable = false)
    private int testCompletionCount;
    
    @Column(nullable = false)
    private int assignmentOpenedCount;
    
    @Column(nullable = false)
    private int assignmentsSubmittedCountValue;
    
    @Column(nullable = false)
    private int lateSubmissionCountValue;
    
    @Column(nullable = false)
    private int missedDeadlineCountValue;
    
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

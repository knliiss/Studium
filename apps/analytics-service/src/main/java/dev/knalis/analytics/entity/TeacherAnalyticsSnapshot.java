package dev.knalis.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "teacher_analytics_snapshots",
        indexes = {
                @Index(name = "idx_teacher_analytics_teacher_id", columnList = "teacherId", unique = true)
        }
)
public class TeacherAnalyticsSnapshot {
    
    @Id
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID teacherId;
    
    @Column(nullable = false)
    private int publishedAssignmentsCount;
    
    @Column(nullable = false)
    private int publishedTestsCount;
    
    @Column(nullable = false)
    private int assignedGradesCount;
    
    @Column
    private Double averageReviewTimeHours;
    
    @Column
    private Double averageStudentScore;
    
    @Column(nullable = false)
    private double failingRate;
    
    @Column(nullable = false)
    private double scoreTotal;
    
    @Column(nullable = false)
    private int scoreCount;
    
    @Column(nullable = false)
    private int failingScoreCount;
    
    @Column(nullable = false)
    private double reviewTimeHoursTotal;
    
    @Column(nullable = false)
    private int reviewTimeSampleCount;
    
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

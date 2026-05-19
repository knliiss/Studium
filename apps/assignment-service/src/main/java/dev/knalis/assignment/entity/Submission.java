package dev.knalis.assignment.entity;

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
        name = "submissions",
        indexes = {
                @Index(name = "idx_submissions_assignment_id", columnList = "assignmentId"),
                @Index(name = "idx_submissions_user_id", columnList = "userId"),
                @Index(name = "idx_submissions_file_id", columnList = "fileId")
        }
)
public class Submission {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID assignmentId;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(nullable = false)
    @ColumnDefault("1")
    private int attemptNumber = 1;

    @Column(nullable = false)
    private boolean submittedLate;

    @Column(length = 2000)
    private String reviewComment;

    @Column
    private Instant reviewedAt;

    @Column
    private UUID reviewedByUserId;

    @Column
    private Instant returnedForRevisionAt;

    @Column
    private Instant revisionDeadline;

    @Column(nullable = false, updatable = false)
    private Instant submittedAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = now;
        }
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

package dev.knalis.assignment.entity;

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
        name = "submission_comments",
        indexes = {
                @Index(name = "idx_submission_comments_submission_id", columnList = "submissionId"),
                @Index(name = "idx_submission_comments_author_user_id", columnList = "authorUserId")
        }
)
public class SubmissionComment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID submissionId;

    @Column(nullable = false)
    private UUID authorUserId;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private boolean deleted;

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

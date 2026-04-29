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

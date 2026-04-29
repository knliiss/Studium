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
        name = "assignment_group_availability",
        indexes = {
                @Index(name = "idx_assignment_group_availability_assignment_id", columnList = "assignmentId"),
                @Index(name = "idx_assignment_group_availability_group_id", columnList = "groupId"),
                @Index(
                        name = "uk_assignment_group_availability_assignment_id_group_id",
                        columnList = "assignmentId, groupId",
                        unique = true
                )
        }
)
public class AssignmentGroupAvailability {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID assignmentId;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private boolean visible;

    @Column
    private Instant availableFrom;

    @Column(nullable = false)
    private Instant deadline;

    @Column(nullable = false)
    private boolean allowLateSubmissions;

    @Column(nullable = false)
    private int maxSubmissions = 1;

    @Column(nullable = false)
    private boolean allowResubmit;

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

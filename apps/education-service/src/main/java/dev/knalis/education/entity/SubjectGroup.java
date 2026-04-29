package dev.knalis.education.entity;

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
        name = "subject_groups",
        indexes = {
                @Index(name = "idx_subject_groups_subject_id", columnList = "subjectId"),
                @Index(name = "idx_subject_groups_group_id", columnList = "groupId"),
                @Index(name = "uk_subject_groups_subject_id_group_id", columnList = "subjectId, groupId", unique = true)
        }
)
public class SubjectGroup {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private UUID groupId;

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

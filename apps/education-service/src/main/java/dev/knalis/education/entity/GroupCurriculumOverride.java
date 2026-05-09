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
        name = "group_curriculum_overrides",
        indexes = {
                @Index(name = "idx_group_curriculum_overrides_group_id", columnList = "group_id"),
                @Index(name = "idx_group_curriculum_overrides_subject_id", columnList = "subject_id"),
                @Index(
                        name = "idx_group_curriculum_overrides_group_subject_unique",
                        columnList = "group_id,subject_id",
                        unique = true
                )
        }
)
public class GroupCurriculumOverride {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "lecture_count_override")
    private Integer lectureCountOverride;

    @Column(name = "practice_count_override")
    private Integer practiceCountOverride;

    @Column(name = "lab_count_override")
    private Integer labCountOverride;

    @Column(length = 1000)
    private String notes;

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

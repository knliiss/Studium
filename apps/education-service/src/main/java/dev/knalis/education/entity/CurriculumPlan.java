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
        name = "curriculum_plans",
        indexes = {
                @Index(
                        name = "idx_curriculum_plans_specialty_year_semester_subject",
                        columnList = "specialty_id,study_year,semester_number,subject_id"
                ),
                @Index(name = "idx_curriculum_plans_active", columnList = "active")
        }
)
public class CurriculumPlan {

    @Id
    private UUID id;

    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;

    @Column(name = "study_year", nullable = false)
    private Integer studyYear;

    @Column(name = "semester_number", nullable = false)
    private Integer semesterNumber;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "lecture_count", nullable = false)
    private Integer lectureCount;

    @Column(name = "practice_count", nullable = false)
    private Integer practiceCount;

    @Column(name = "lab_count", nullable = false)
    private Integer labCount;

    @Column(name = "supports_stream_lecture", nullable = false)
    private boolean supportsStreamLecture;

    @Column(name = "requires_subgroups_for_labs", nullable = false)
    private boolean requiresSubgroupsForLabs;

    @Column(nullable = false)
    private boolean active;

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

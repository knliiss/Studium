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
        name = "subject_teachers",
        indexes = {
                @Index(name = "idx_subject_teachers_subject_id", columnList = "subjectId"),
                @Index(name = "idx_subject_teachers_teacher_id", columnList = "teacherId"),
                @Index(name = "uk_subject_teachers_subject_id_teacher_id", columnList = "subjectId, teacherId", unique = true)
        }
)
public class SubjectTeacher {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private UUID teacherId;

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

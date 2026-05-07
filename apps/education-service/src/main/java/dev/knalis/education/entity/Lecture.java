package dev.knalis.education.entity;

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
        name = "lectures",
        indexes = {
                @Index(name = "idx_lectures_subject_id", columnList = "subjectId"),
                @Index(name = "idx_lectures_topic_id", columnList = "topicId"),
                @Index(name = "idx_lectures_status", columnList = "status"),
                @Index(name = "idx_lectures_created_by_user_id", columnList = "createdByUserId")
        }
)
public class Lecture {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private UUID topicId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 10000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LectureStatus status = LectureStatus.DRAFT;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int orderIndex;

    @Column
    private UUID createdByUserId;

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


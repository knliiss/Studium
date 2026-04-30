package dev.knalis.schedule.entity;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "teacher_debts",
        indexes = {
                @Index(name = "idx_teacher_debts_teacher_id", columnList = "teacherId"),
                @Index(name = "idx_teacher_debts_group_id", columnList = "groupId"),
                @Index(name = "idx_teacher_debts_subject_id", columnList = "subjectId"),
                @Index(name = "idx_teacher_debts_date", columnList = "date"),
                @Index(name = "idx_teacher_debts_slot_id", columnList = "slotId"),
                @Index(name = "idx_teacher_debts_status", columnList = "status")
        }
)
public class TeacherDebt {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID scheduleOverrideId;

    @Column(nullable = false)
    private UUID teacherId;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private UUID slotId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LessonType lessonType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ALL'")
    private Subgroup subgroup = Subgroup.ALL;

    @Column(length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeacherDebtStatus status = TeacherDebtStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant resolvedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (subgroup == null) {
            subgroup = Subgroup.ALL;
        }
        if (status == null) {
            status = TeacherDebtStatus.OPEN;
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

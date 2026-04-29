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
        name = "schedule_overrides",
        indexes = {
                @Index(name = "idx_schedule_overrides_semester_id", columnList = "semesterId"),
                @Index(name = "idx_schedule_overrides_template_id", columnList = "templateId"),
                @Index(name = "idx_schedule_overrides_date", columnList = "date"),
                @Index(name = "idx_schedule_overrides_group_id", columnList = "groupId"),
                @Index(name = "idx_schedule_overrides_subject_id", columnList = "subjectId"),
                @Index(name = "idx_schedule_overrides_teacher_id", columnList = "teacherId"),
                @Index(name = "idx_schedule_overrides_room_id", columnList = "roomId"),
                @Index(name = "idx_schedule_overrides_slot_id", columnList = "slotId"),
                @Index(name = "idx_schedule_overrides_override_type", columnList = "overrideType")
        }
)
public class ScheduleOverride {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID semesterId;
    
    @Column
    private UUID templateId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OverrideType overrideType;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false)
    private UUID groupId;
    
    @Column(nullable = false)
    private UUID subjectId;
    
    @Column(nullable = false)
    private UUID teacherId;
    
    @Column(nullable = false)
    private UUID slotId;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LessonType lessonType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LessonFormat lessonFormat;
    
    @Column
    private UUID roomId;
    
    @Column(length = 500)
    private String onlineMeetingUrl;
    
    @Column(length = 2000)
    private String notes;
    
    @Column(nullable = false)
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

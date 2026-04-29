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

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "schedule_templates",
        indexes = {
                @Index(name = "idx_schedule_templates_semester_id", columnList = "semesterId"),
                @Index(name = "idx_schedule_templates_group_id", columnList = "groupId"),
                @Index(name = "idx_schedule_templates_subject_id", columnList = "subjectId"),
                @Index(name = "idx_schedule_templates_teacher_id", columnList = "teacherId"),
                @Index(name = "idx_schedule_templates_room_id", columnList = "roomId"),
                @Index(name = "idx_schedule_templates_slot_id", columnList = "slotId"),
                @Index(name = "idx_schedule_templates_day_of_week", columnList = "dayOfWeek"),
                @Index(name = "idx_schedule_templates_week_type", columnList = "weekType"),
                @Index(name = "idx_schedule_templates_active", columnList = "active"),
                @Index(name = "idx_schedule_templates_status", columnList = "status")
        }
)
public class ScheduleTemplate {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID semesterId;
    
    @Column(nullable = false)
    private UUID groupId;
    
    @Column(nullable = false)
    private UUID subjectId;
    
    @Column(nullable = false)
    private UUID teacherId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DayOfWeek dayOfWeek;
    
    @Column(nullable = false)
    private UUID slotId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WeekType weekType;
    
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleTemplateStatus status = ScheduleTemplateStatus.ACTIVE;
    
    @Column(nullable = false)
    private boolean active = true;
    
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

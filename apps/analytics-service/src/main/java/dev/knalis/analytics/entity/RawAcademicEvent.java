package dev.knalis.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "raw_academic_events",
        indexes = {
                @Index(name = "uk_raw_academic_events_event_id", columnList = "eventId", unique = true),
                @Index(name = "idx_raw_academic_events_event_type", columnList = "eventType"),
                @Index(name = "idx_raw_academic_events_user_id", columnList = "userId"),
                @Index(name = "idx_raw_academic_events_group_id", columnList = "groupId"),
                @Index(name = "idx_raw_academic_events_subject_id", columnList = "subjectId"),
                @Index(name = "idx_raw_academic_events_teacher_id", columnList = "teacherId"),
                @Index(name = "idx_raw_academic_events_occurred_at", columnList = "occurredAt")
        }
)
public class RawAcademicEvent {
    
    @Id
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID eventId;
    
    @Column(nullable = false, length = 150)
    private String eventType;
    
    @Column
    private UUID userId;
    
    @Column
    private UUID teacherId;
    
    @Column
    private UUID groupId;
    
    @Column
    private UUID subjectId;
    
    @Column
    private UUID topicId;
    
    @Column
    private UUID assignmentId;
    
    @Column
    private UUID submissionId;
    
    @Column
    private UUID testId;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payloadJson;
    
    @Column(nullable = false)
    private Instant occurredAt;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}

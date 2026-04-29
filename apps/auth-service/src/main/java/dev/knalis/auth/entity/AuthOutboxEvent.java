package dev.knalis.auth.entity;

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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "auth_outbox_events",
        indexes = {
                @Index(name = "idx_auth_outbox_status_next_attempt", columnList = "status,nextAttemptAt"),
                @Index(name = "idx_auth_outbox_created_at", columnList = "createdAt"),
                @Index(name = "idx_auth_outbox_processing_started_at", columnList = "processingStartedAt")
        }
)
public class AuthOutboxEvent {
    
    @Id
    private UUID id;
    
    @Column(nullable = false, length = 255)
    private String topic;
    
    @Column(nullable = false, length = 255)
    private String messageKey;
    
    @Column(nullable = false, length = 255)
    private String eventType;
    
    @Column(nullable = false, length = 500)
    private String payloadType;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthOutboxStatus status = AuthOutboxStatus.PENDING;
    
    @Column(nullable = false)
    private int attemptCount = 0;
    
    @Column(nullable = false)
    private Instant nextAttemptAt;
    
    @Column
    private Instant processingStartedAt;
    
    @Column
    private Instant publishedAt;
    
    @Column(length = 2000)
    private String lastError;
    
    @Column
    private Integer publishedPartition;
    
    @Column
    private Long publishedOffset;
    
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
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
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

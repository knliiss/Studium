package dev.knalis.audit.entity;

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
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_events_actor_user_id", columnList = "actorUserId"),
                @Index(name = "idx_audit_events_entity_type_entity_id", columnList = "entityType,entityId"),
                @Index(name = "idx_audit_events_occurred_at", columnList = "occurredAt"),
                @Index(name = "idx_audit_events_source_service", columnList = "sourceService")
        }
)
public class AuditEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 100)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(columnDefinition = "TEXT")
    private String oldValueJson;

    @Column(columnDefinition = "TEXT")
    private String newValueJson;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = 100)
    private String requestId;

    @Column(nullable = false, length = 100)
    private String sourceService;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}

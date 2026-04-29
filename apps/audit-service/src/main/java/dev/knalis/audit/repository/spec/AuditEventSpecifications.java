package dev.knalis.audit.repository.spec;

import dev.knalis.audit.entity.AuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> hasActorId(UUID actorId) {
        return (root, query, cb) -> actorId == null ? cb.conjunction() : cb.equal(root.get("actorUserId"), actorId);
    }

    public static Specification<AuditEvent> hasEntityType(String entityType) {
        return (root, query, cb) -> entityType == null || entityType.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get("entityType"), entityType.trim());
    }

    public static Specification<AuditEvent> hasEntityId(UUID entityId) {
        return (root, query, cb) -> entityId == null ? cb.conjunction() : cb.equal(root.get("entityId"), entityId);
    }

    public static Specification<AuditEvent> occurredAtFrom(Instant dateFrom) {
        return (root, query, cb) -> dateFrom == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("occurredAt"), dateFrom);
    }

    public static Specification<AuditEvent> occurredAtTo(Instant dateTo) {
        return (root, query, cb) -> dateTo == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("occurredAt"), dateTo);
    }
}

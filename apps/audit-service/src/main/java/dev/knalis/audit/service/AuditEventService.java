package dev.knalis.audit.service;

import dev.knalis.audit.dto.request.CreateAuditEventRequest;
import dev.knalis.audit.dto.response.AuditEventPageResponse;
import dev.knalis.audit.dto.response.AuditEventResponse;
import dev.knalis.audit.entity.AuditEvent;
import dev.knalis.audit.exception.AuditEventNotFoundException;
import dev.knalis.audit.mapper.AuditEventMapper;
import dev.knalis.audit.repository.AuditEventRepository;
import dev.knalis.audit.repository.spec.AuditEventSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "occurredAt",
            "sourceService",
            "action",
            "entityType"
    );

    private final AuditEventRepository auditEventRepository;
    private final AuditEventMapper auditEventMapper;

    @Transactional
    public AuditEventResponse createAuditEvent(CreateAuditEventRequest request) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setId(request.id());
        auditEvent.setActorUserId(request.actorUserId());
        auditEvent.setAction(request.action().trim());
        auditEvent.setEntityType(request.entityType().trim());
        auditEvent.setEntityId(request.entityId());
        auditEvent.setOldValueJson(blankToNull(request.oldValueJson()));
        auditEvent.setNewValueJson(blankToNull(request.newValueJson()));
        auditEvent.setOccurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt());
        auditEvent.setRequestId(blankToNull(request.requestId()));
        auditEvent.setSourceService(request.sourceService().trim());
        return auditEventMapper.toResponse(auditEventRepository.save(auditEvent));
    }

    @Transactional(readOnly = true)
    public AuditEventPageResponse getAuditEvents(
            UUID actorId,
            String entityType,
            UUID entityId,
            Instant dateFrom,
            Instant dateTo,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Specification<AuditEvent> specification = Specification.<AuditEvent>allOf()
                .and(AuditEventSpecifications.hasActorId(actorId))
                .and(AuditEventSpecifications.hasEntityType(entityType))
                .and(AuditEventSpecifications.hasEntityId(entityId))
                .and(AuditEventSpecifications.occurredAtFrom(dateFrom))
                .and(AuditEventSpecifications.occurredAtTo(dateTo));

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );

        Page<AuditEvent> auditPage = auditEventRepository.findAll(specification, pageRequest);
        return new AuditEventPageResponse(
                auditPage.getContent().stream().map(auditEventMapper::toResponse).toList(),
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements(),
                auditPage.getTotalPages(),
                auditPage.isFirst(),
                auditPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public AuditEventResponse getAuditEvent(UUID auditEventId) {
        AuditEvent auditEvent = auditEventRepository.findById(auditEventId)
                .orElseThrow(() -> new AuditEventNotFoundException(auditEventId));
        return auditEventMapper.toResponse(auditEvent);
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "occurredAt";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "occurredAt";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

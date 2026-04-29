package dev.knalis.schedule.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.schedule.client.audit.AuditServiceClient;
import dev.knalis.schedule.client.audit.dto.CreateAuditEventRequest;
import dev.knalis.shared.web.request.RequestCorrelationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAuditService {

    private static final String SOURCE_SERVICE = "schedule-service";

    private final AuditServiceClient auditServiceClient;
    private final ObjectMapper objectMapper;

    public void record(UUID actorUserId, String action, String entityType, UUID entityId, Object oldValue, Object newValue) {
        try {
            auditServiceClient.createAuditEvent(new CreateAuditEventRequest(
                    null,
                    actorUserId,
                    action,
                    entityType,
                    entityId,
                    toJson(oldValue),
                    toJson(newValue),
                    Instant.now(),
                    RequestCorrelationContext.getCurrentRequestId(),
                    SOURCE_SERVICE
            ));
        } catch (Exception exception) {
            log.warn(
                    "Failed to publish schedule audit event action={} entityType={} entityId={}",
                    action,
                    entityType,
                    entityId,
                    exception
            );
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"serializationError\":true}";
        }
    }
}

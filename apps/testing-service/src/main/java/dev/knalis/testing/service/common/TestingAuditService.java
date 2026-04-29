package dev.knalis.testing.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.shared.web.request.RequestCorrelationContext;
import dev.knalis.testing.client.audit.AuditServiceClient;
import dev.knalis.testing.client.audit.dto.CreateAuditEventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestingAuditService {

    private static final String SOURCE_SERVICE = "testing-service";

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
                    "Failed to publish testing audit event action={} entityType={} entityId={}",
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

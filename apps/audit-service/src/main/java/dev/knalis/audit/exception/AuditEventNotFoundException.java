package dev.knalis.audit.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AuditEventNotFoundException extends AppException {

    public AuditEventNotFoundException(UUID auditEventId) {
        super(
                HttpStatus.NOT_FOUND,
                "AUDIT_EVENT_NOT_FOUND",
                "Audit event was not found",
                Map.of("auditEventId", auditEventId)
        );
    }
}

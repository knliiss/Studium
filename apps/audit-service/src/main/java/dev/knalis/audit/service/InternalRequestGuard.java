package dev.knalis.audit.service;

import dev.knalis.audit.config.AuditInternalProperties;
import dev.knalis.audit.exception.InvalidInternalRequestException;
import org.springframework.stereotype.Service;

@Service
public class InternalRequestGuard {

    private final AuditInternalProperties auditInternalProperties;

    public InternalRequestGuard(AuditInternalProperties auditInternalProperties) {
        this.auditInternalProperties = auditInternalProperties;
    }

    public void verify(String sharedSecret) {
        if (sharedSecret == null || !sharedSecret.equals(auditInternalProperties.getSharedSecret())) {
            throw new InvalidInternalRequestException();
        }
    }
}

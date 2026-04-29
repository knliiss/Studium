package dev.knalis.audit.mapper;

import dev.knalis.audit.dto.response.AuditEventResponse;
import dev.knalis.audit.entity.AuditEvent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditEventMapper {

    AuditEventResponse toResponse(AuditEvent auditEvent);
}

package dev.knalis.audit.dto.response;

import java.util.List;

public record AuditEventPageResponse(
        List<AuditEventResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

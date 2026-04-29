package dev.knalis.gateway.client.audit.dto;

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
    public static AuditEventPageResponse empty() {
        return new AuditEventPageResponse(List.of(), 0, 0, 0, 0, true, true);
    }
}

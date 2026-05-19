package dev.knalis.gateway.client.assignment.dto;

import java.util.List;

public record AssignmentPageResponse(
        List<AssignmentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

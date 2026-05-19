package dev.knalis.gateway.client.testing.dto;

import java.util.List;

public record TestResultPageResponse(
        List<TestResultResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

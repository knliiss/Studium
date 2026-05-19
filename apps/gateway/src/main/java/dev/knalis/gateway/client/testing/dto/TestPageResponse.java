package dev.knalis.gateway.client.testing.dto;

import java.util.List;

public record TestPageResponse(
        List<TestResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

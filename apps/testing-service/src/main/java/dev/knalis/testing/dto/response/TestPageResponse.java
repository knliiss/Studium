package dev.knalis.testing.dto.response;

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

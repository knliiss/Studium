package dev.knalis.testing.dto.response;

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

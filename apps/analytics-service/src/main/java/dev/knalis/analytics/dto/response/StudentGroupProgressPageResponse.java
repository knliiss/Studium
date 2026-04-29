package dev.knalis.analytics.dto.response;

import java.util.List;

public record StudentGroupProgressPageResponse(
        List<StudentGroupProgressResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

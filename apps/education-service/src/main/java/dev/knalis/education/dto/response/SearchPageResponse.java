package dev.knalis.education.dto.response;

import java.util.List;

public record SearchPageResponse(
        List<SearchItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

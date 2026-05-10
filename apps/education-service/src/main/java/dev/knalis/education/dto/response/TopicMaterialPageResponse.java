package dev.knalis.education.dto.response;

import java.util.List;

public record TopicMaterialPageResponse(
        List<TopicMaterialResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}


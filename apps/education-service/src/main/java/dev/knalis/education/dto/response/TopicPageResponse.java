package dev.knalis.education.dto.response;

import java.util.List;

public record TopicPageResponse(
        List<TopicResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

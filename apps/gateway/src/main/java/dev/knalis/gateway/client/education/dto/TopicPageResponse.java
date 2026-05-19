package dev.knalis.gateway.client.education.dto;

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

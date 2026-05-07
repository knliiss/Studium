package dev.knalis.education.dto.response;

import java.util.List;

public record LecturePageResponse(
        List<LectureResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}


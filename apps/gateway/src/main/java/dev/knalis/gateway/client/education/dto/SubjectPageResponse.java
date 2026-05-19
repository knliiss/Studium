package dev.knalis.gateway.client.education.dto;

import java.util.List;

public record SubjectPageResponse(
        List<SubjectResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}

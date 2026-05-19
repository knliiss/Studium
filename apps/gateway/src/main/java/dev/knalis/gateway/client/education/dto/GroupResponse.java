package dev.knalis.gateway.client.education.dto;

import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        Integer studyYear
) {
}

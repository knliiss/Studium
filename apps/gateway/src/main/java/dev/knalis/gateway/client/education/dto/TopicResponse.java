package dev.knalis.gateway.client.education.dto;

import java.util.UUID;

public record TopicResponse(
        UUID id,
        UUID subjectId,
        String title,
        int orderIndex
) {
}

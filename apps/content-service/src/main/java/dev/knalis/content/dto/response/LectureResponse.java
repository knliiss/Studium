package dev.knalis.content.dto.response;

import dev.knalis.content.entity.LectureStatus;

import java.time.Instant;
import java.util.UUID;

public record LectureResponse(
        UUID id,
        UUID topicId,
        String title,
        String summary,
        String content,
        int orderIndex,
        LectureStatus status,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}


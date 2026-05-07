package dev.knalis.education.dto.response;

import dev.knalis.education.entity.LectureStatus;

import java.time.Instant;
import java.util.UUID;

public record LectureResponse(
        UUID id,
        UUID subjectId,
        UUID topicId,
        String title,
        String content,
        LectureStatus status,
        int orderIndex,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}



package dev.knalis.testing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TestStudentAnswerOptionResponse(
        UUID id,
        UUID questionId,
        String text,
        Instant createdAt,
        Instant updatedAt
) {
}

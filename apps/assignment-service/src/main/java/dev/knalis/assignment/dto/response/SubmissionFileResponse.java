package dev.knalis.assignment.dto.response;

import java.util.UUID;

public record SubmissionFileResponse(
        UUID id,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String status
) {
}

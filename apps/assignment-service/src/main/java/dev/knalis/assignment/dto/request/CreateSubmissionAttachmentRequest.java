package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSubmissionAttachmentRequest(
        @NotNull
        UUID fileId,
        @Size(max = 255)
        String displayName
) {
}

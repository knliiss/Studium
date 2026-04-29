package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubmissionRequest(
        
        @NotNull
        UUID assignmentId,
        
        @NotNull
        UUID fileId
) {
}

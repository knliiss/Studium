package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ResubmitSubmissionRequest(

        @NotNull
        UUID fileId
) {
}

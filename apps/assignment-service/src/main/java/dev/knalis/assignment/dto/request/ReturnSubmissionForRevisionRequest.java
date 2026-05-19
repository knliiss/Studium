package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ReturnSubmissionForRevisionRequest(

        @NotBlank
        @Size(max = 2000)
        String feedback,

        @Future
        Instant revisionDeadline
) {
}

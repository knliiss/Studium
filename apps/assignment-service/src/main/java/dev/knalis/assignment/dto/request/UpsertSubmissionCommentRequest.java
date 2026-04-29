package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertSubmissionCommentRequest(

        @NotBlank
        @Size(max = 2000)
        String body
) {
}

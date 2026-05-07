package dev.knalis.testing.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuestionAnswerSubmissionRequest(

        @NotNull
        UUID questionId,

        @NotNull
        JsonNode value
) {
}


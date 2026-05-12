package dev.knalis.testing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTestResultQuestionScoreRequest(

        @NotNull
        @Min(0)
        Integer score,

        @Size(max = 2000)
        String comment
) {
}

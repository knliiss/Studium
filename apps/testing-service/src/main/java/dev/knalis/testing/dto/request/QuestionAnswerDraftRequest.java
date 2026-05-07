package dev.knalis.testing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionAnswerDraftRequest(

        @NotBlank
        @Size(max = 2000)
        String text,

        Boolean isCorrect
) {
}

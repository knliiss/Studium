package dev.knalis.testing.dto.request;

import dev.knalis.testing.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateQuestionRequest(

        @NotBlank
        @Size(max = 2000)
        String text,

        QuestionType type,

        @Size(max = 2000)
        String description,

        @Min(0)
        Integer points,

        @PositiveOrZero
        Integer orderIndex,

        Boolean required,

        @Size(max = 2000)
        String feedback,

        @Size(max = 8000)
        String configurationJson,

        List<@Valid QuestionAnswerDraftRequest> answers
) {
}

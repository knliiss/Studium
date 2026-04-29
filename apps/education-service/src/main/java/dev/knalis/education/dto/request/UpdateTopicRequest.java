package dev.knalis.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTopicRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotNull
        @PositiveOrZero
        Integer orderIndex
) {
}

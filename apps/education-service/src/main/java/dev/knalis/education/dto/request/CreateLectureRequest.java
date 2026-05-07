package dev.knalis.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateLectureRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 10000)
        String content,

        @NotNull
        @PositiveOrZero
        Integer orderIndex
) {
}


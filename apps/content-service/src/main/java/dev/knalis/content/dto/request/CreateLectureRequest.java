package dev.knalis.content.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLectureRequest(

        @NotNull
        UUID topicId,

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 1000)
        String summary,

        @NotBlank
        String content,

        @NotNull
        @Min(0)
        Integer orderIndex
) {
}


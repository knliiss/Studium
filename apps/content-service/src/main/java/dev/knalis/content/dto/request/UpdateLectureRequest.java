package dev.knalis.content.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateLectureRequest(

        @Size(max = 200)
        String title,

        @Size(max = 1000)
        String summary,

        String content,

        @Min(0)
        Integer orderIndex
) {
}


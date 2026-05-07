package dev.knalis.education.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveLectureRequest(

        @NotNull
        UUID topicId,

        @NotNull
        @Min(0)
        Integer orderIndex
) {
}


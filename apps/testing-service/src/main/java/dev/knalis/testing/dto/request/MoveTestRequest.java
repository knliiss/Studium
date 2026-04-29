package dev.knalis.testing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveTestRequest(

        @NotNull
        UUID topicId,

        @NotNull
        @Min(0)
        Integer orderIndex
) {
}

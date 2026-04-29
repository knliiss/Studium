package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveAssignmentRequest(

        @NotNull
        UUID topicId,

        @NotNull
        @Min(0)
        Integer orderIndex
) {
}

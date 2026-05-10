package dev.knalis.education.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveTopicMaterialRequest(
        @NotNull UUID topicId,
        @NotNull Integer orderIndex
) {
}


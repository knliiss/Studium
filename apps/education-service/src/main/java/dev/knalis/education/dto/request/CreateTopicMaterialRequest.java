package dev.knalis.education.dto.request;

import dev.knalis.education.entity.TopicMaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTopicMaterialRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull TopicMaterialType type,
        @Size(max = 2000) String url,
        UUID fileId,
        Boolean visible,
        Integer orderIndex
) {
}


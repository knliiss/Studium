package dev.knalis.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLectureMaterialRequest(

        @NotNull
        UUID fileId,

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 1000)
        String description
) {
}


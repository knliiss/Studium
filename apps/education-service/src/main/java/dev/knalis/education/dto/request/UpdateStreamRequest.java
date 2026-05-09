package dev.knalis.education.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateStreamRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @NotNull
        UUID specialtyId,

        @NotNull
        @Min(1)
        @Max(8)
        Integer studyYear
) {
}

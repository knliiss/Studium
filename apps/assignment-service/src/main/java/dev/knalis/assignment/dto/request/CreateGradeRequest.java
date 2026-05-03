package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateGradeRequest(
        
        @NotNull
        UUID submissionId,
        
        @NotNull
        @Min(0)
        Integer score,
        
        @Size(max = 2000)
        String feedback
) {
}

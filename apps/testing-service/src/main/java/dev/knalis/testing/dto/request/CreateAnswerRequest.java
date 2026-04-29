package dev.knalis.testing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAnswerRequest(
        
        @NotNull
        UUID questionId,
        
        @NotBlank
        @Size(max = 2000)
        String text,
        
        @NotNull
        Boolean isCorrect
) {
}

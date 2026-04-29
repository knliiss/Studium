package dev.knalis.testing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateTestRequest(
        
        @NotNull
        UUID topicId,
        
        @NotBlank
        @Size(max = 200)
        String title,
        
        @Min(1)
        Integer maxAttempts,

        @Min(1)
        Integer maxPoints,
        
        @Min(1)
        Integer timeLimitMinutes,
        
        Instant availableFrom,
        
        Instant availableUntil,
        
        Boolean showCorrectAnswersAfterSubmit,
        
        Boolean shuffleQuestions,
        
        Boolean shuffleAnswers,

        @Min(0)
        Integer orderIndex
) {
}
